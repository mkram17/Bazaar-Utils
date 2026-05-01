package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Abstract base for snapshot-based data sources — screen parses and API polls.
 * Peer to {@link ChatOrderSource} in the pipeline layer; they share no inheritance.
 *
 * <p>Two commit paths:
 *
 * <ol>
 *   <li><b>Market data path</b> — {@link #snapshotProduct} / {@link #commitProduct}.
 *       {@code snapshotProduct} applies book levels and returns raw
 *       {@link FillInference.Result}s for the caller to accumulate; the caller calls
 *       {@link FillInference#applyAll} when ready (immediately for single-product
 *       sources, once after a full API loop for batched sources).
 *       {@code commitProduct} is the single-product convenience wrapper.</li>
 *
 *   <li><b>Order reconciliation path</b> — {@link #commitAll}.
 *       Receives a {@link List} of {@link OrderDelta}s produced by the resolution
 *       phase of {@code OrdersScreenDataSource}. Composes all mutations in one pass
 *       via {@link OrderDelta#mutation()}, performs one atomic storage write (no
 *       {@code reindexActive} — screen positions are authoritative), fires events
 *       by switching on delta type, persists once, and posts
 *       {@link BazaarDataUpdateEvent}.</li>
 * </ol>
 */
public abstract class SnapshotSource extends BUListener {
    /**
     * Applies book levels for one product and computes (but does not apply) fill
     * inferences.
     *
     * <p>The {@code inferrableOrders} supplier is evaluated <em>after</em>
     * {@code data.apply()} has run on both sides. This is load-bearing for
     * {@code ApiSnapshotDataSource}, whose {@code isWithinSnapshotWindow()} check
     * depends on the post-apply book boundaries ({@code lastEntry()} of each side).
     *
     * @param inferrableOrders supplier evaluated post-apply; returns orders eligible
     *                         for fill inference. Pass {@code __ -> List.of()} to skip.
     * @param notifType        notification channel for inference log lines.
     * @return raw inferences — caller must call {@link FillInference#applyAll}.
     */
    protected final List<FillInference.Result> snapshotProduct(
            String productId,
            List<PriceLevel> askLevels,
            List<PriceLevel> bidLevels,
            Function<ProductData, List<Order>> inferrableOrders,
            NotificationType notifType,
            BazaarDataOrigin.Snapshot origin) {
        var data = BazaarDataRegistry.getOrCreate(productId);

        data.apply(PriceType.INSTABUY,  askLevels, origin);
        data.apply(PriceType.INSTASELL, bidLevels,  origin);

        var orders = inferrableOrders.apply(data);

        return FillInference.infer(productId, data, orders, origin, notifType);
    }

    /**
     * Convenience overload for callers with a static, pre-computed order list
     * (no post-apply dependency on book state).
     */
    protected final List<FillInference.Result> snapshotProduct(
            String productId,
            List<PriceLevel> askLevels,
            List<PriceLevel> bidLevels,
            List<Order> inferrableOrders,
            NotificationType notifType,
            BazaarDataOrigin.Snapshot origin) {
        return snapshotProduct(productId, askLevels, bidLevels, __ -> inferrableOrders, notifType, origin);
    }

    /**
     * Full single-product commit: {@link #snapshotProduct} then
     * {@link FillInference#applyAll} in one step.
     *
     * <p>Use for single-product sources ({@code PageSummaryDataSource}). For batched
     * multi-product sources, call {@link #snapshotProduct} per product, accumulate all
     * inferences, and call {@code FillInference.applyAll} once after the loop to keep
     * storage writes to one per API tick.
     *
     * @return {@code true} if book or fill state changed.
     */
    protected final boolean commitProduct(
            String productId,
            List<PriceLevel> askLevels,
            List<PriceLevel> bidLevels,
            List<Order> inferrableOrders,
            NotificationType notifType,
            BazaarDataOrigin.Snapshot origin) {
        var inferences = snapshotProduct(productId, askLevels, bidLevels, inferrableOrders, notifType, origin);

        return FillInference.applyAll(inferences, origin);
    }

    /**
     * Batch commit boundary for the orders-screen reconciliation path.
     *
     * <p>Receives the fully-resolved {@link OrderDelta} list from
     * {@code OrdersScreenDataSource#reconcileProduct} and owns all infrastructure
     * from this point on. The caller must not touch storage, the book, or events
     * after this call returns.
     *
     * <p>The {@code unrelated} list carries orders for other products that must be
     * written through unchanged — they are never inspected here, only threaded into
     * the storage write.
     *
     * <h3>Steps</h3>
     * <ol>
     *   <li>Apply the pre-composed {@code mutation} — covers eviction decrements, fill
     *       decrements, price-correction decrement+place pairs, and floor-affirmation
     *       {@link BookMutation.Floor} entries, composed by the caller.</li>
     *   <li>Change detection — {@code !deltas.isEmpty()} signals an order-state change;
     *       early-returns if neither orders nor book mutated.</li>
     *   <li>Atomic storage write — no {@code reindexActive}; screen slot positions
     *       are already stamped on each order by the resolution phase.</li>
     *   <li>Event dispatch per delta type.</li>
     *   <li>{@link BazaarDataUpdateEvent} — always posted past the early exit.</li>
     * </ol>
     *
     * @param deltas    fully-resolved order-state deltas for this product — Place, Evict,
     *                  Update, and PriceCorrection entries only. BookOnly and None never
     *                  appear here; book mutation is carried by the {@code mutation} parameter.
     * @param unrelated orders for other products — written through unchanged.
     * @param preserved orders retained unchanged (grace window / terminal / post-obs).
     * @param offScreen orders retained unchanged (unanchored, no confirmed slot).
     * @param unchanged  in-common (state-identical) orders — written through to storage
     *                   without firing any event. Pure slot-reanchor orders are carried as
     *                   {@link OrderDelta.Reanchor} entries in {@code deltas} so that the
     *                   {@code deltas.isEmpty()} early-return is bypassed and the updated
     *                   {@link OrderSlotPosition} is persisted.
     * @param mutation   pre-composed book mutation for this product — applied once before
     *                   the storage write, covering all eviction, fill, and floor effects.
     */
    protected final void commitAll(
            String productId,
            BookMutation mutation,
            List<OrderDelta> deltas,
            List<Order> unrelated,
            List<Order> preserved,
            List<Order> offScreen,
            List<Order> unchanged,
            BazaarDataOrigin.OrdersScreen origin) {

        // ── 1. Apply composed mutation ────────────────────────
        boolean bookChanged = mutation.apply(productId, origin);

        // ── 2. Change detection ───────────────────────────────────────────────
        boolean anyOrderChange = !deltas.isEmpty();
        if (!anyOrderChange && !bookChanged) return;

        // ── 4. Atomic storage write ───────────────────────────────────────────
        if (anyOrderChange) {
            // Collect the post-reconciliation order list:
            //   • unrelated   — pass-through unchanged
            //   • preserved   — grace / terminal / post-observation retains
            //   • offScreen   — unanchored retains
            //   • unchanged   — in-common (no-delta) + pure slot-reanchor orders
            //   • deltas      — Place (new), Evict (cancelled / auto-claimed), Update, PriceCorrection
            //
            // No reindexActive — screen positions are authoritative on every order
            // that came through the resolution phase.
            var next = new ArrayList<Order>(unrelated.size() + preserved.size() + offScreen.size() + deltas.size());

            next.addAll(unrelated);
            next.addAll(preserved);
            next.addAll(offScreen);
            next.addAll(unchanged);

            List<UserOrderEvent> events = new ArrayList<>(List.of());

            for (var delta : deltas) {
                switch (delta) {
                    case OrderDelta.Place place -> {
                        next.add(place.order());
                        events.add(new UserOrderEvent.Placed(place.order()));
                    }
                    case OrderDelta.Evict eviction -> {
                        next.add(eviction.order());
                        events.add(new UserOrderEvent.Cancelled(eviction.order()));
                    }
                    case OrderDelta.Update update -> {
                        next.add(update.after());
                        events.addAll(getMutationEvents(update.before(), update.after()));
                    }
                    case OrderDelta.PriceCorrection correction -> {
                        next.add(correction.after());
                        events.addAll(getMutationEvents(correction.before(), correction.after()));
                    }
                    case OrderDelta.Reanchor reanchor -> next.add(reanchor.after());
                    default -> {}
                }
            }

            // Screen slot positions are authoritative.
            // reconcileExisting stamps every matched order with the screen's exact slot;
            // synthesizeNew does the same for new orders. Recomputing positions here
            // overrides the screen's layout and breaks same-placedAt tiebreaking.
            var finalNext = next;
            UserOrdersStorage.apply(ignored -> finalNext);

            events.forEach((event) -> event.post(BazaarUtils.EVENT_BUS));

            if (NotificationType.ORDERDATA.isEnabled()) {
                long placed = deltas.stream().filter(it -> it instanceof OrderDelta.Place).count();
                long evicted = deltas.stream().filter(it -> it instanceof OrderDelta.Evict).count();
                long updated = deltas.stream().filter(it -> it instanceof OrderDelta.Update).count();
                long corrected = deltas.stream().filter(it -> it instanceof OrderDelta.PriceCorrection).count();
                long reanchored = deltas.stream().filter(it -> it instanceof OrderDelta.Reanchor).count();

                PlayerActionUtil.notifyAll("%s — %s committed: Δ%d placed, Δ%d evicted, Δ%d updated, Δ%d price-corrected, Δ%d reanchored".formatted(
                        origin.describe(), productId, placed, evicted, updated, corrected, reanchored), NotificationType.ORDERDATA);
            }
        }

        new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS);
    }

    private static List<UserOrderEvent> getMutationEvents(Order before, Order after) {
        var result = new ArrayList<UserOrderEvent>(List.of());

        if (after.status() instanceof OrderStatus.Filled && !(before.status() instanceof OrderStatus.Filled)) {
            result.add(new UserOrderEvent.Filled(after));
        } else if (after.status() instanceof OrderStatus.Partial) {
            if (!(before.status() instanceof OrderStatus.Partial) || after.filledAmount() > before.filledAmount()) {
                result.add(new UserOrderEvent.PartiallyFilled(after, after.filledAmount() - before.filledAmount()));
            }
        }

        if (after.claimedAmount() > before.claimedAmount()) {
            result.add(new UserOrderEvent.Claimed(after, after.claimedAmount() - before.claimedAmount()));
        }

        return result;
    }
}