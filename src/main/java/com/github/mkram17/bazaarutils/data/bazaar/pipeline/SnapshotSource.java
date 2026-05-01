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
 * Base for sources that commit market snapshots and infer fill advances from book movement.
 * Not related to {@link ChatOrderSource} by inheritance; the two commit layers are peers.
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
 *   <li><b>Order reconciliation path</b> — {@link #stageCommit} / {@link #commitAll}.
 *       {@code stageCommit} resolves one product's book mutation and
 *       {@link OrderDelta}s — produced by {@code OrdersScreenDataSource}'s two-phase
 *       resolution, see its class doc — into a {@link StagedCommit}, without touching
 *       storage. Once every product touched by a screen load has been staged this way,
 *       {@code commitAll} folds them into one atomic storage write, fires their events
 *       by switching on delta type, and posts {@link BazaarDataUpdateEvent} per changed
 *       product. Staging is separated from writing because
 *       {@link UserOrdersStorage#apply} indexes live orders by container slot across the
 *       entire tracked-order list on every call — see {@code commitAll}'s doc.</li>
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
     * One product's contribution to a batched {@link #commitAll} write, produced by
     * {@link #stageCommit}: the orders it contributes to the combined order list, and the
     * events to fire once that write lands. {@code changed} governs whether
     * {@link BazaarDataUpdateEvent} is posted for this product.
     */
    protected record StagedCommit(String productId, List<Order> orders, List<UserOrderEvent> events, boolean changed) {}

    /**
     * Resolves one product's book mutation and order deltas into a {@link StagedCommit}.
     * Does not touch {@link UserOrdersStorage} — every product touched by a screen load
     * must be staged before any of them reaches storage; see {@link #commitAll}.
     *
     * <p>The book mutation is applied immediately, not deferred: {@link BazaarDataRegistry}
     * books are keyed per product, independent of the tracked-order list, so unlike the
     * order list itself they carry no cross-product invariant and need no batching.
     *
     * @param deltas    fully-resolved order-state deltas for this product — Place, Evict,
     *                  Update, and PriceCorrection entries only. BookOnly and None never
     *                  appear here; book mutation is carried by the {@code mutation} parameter.
     * @param preserved orders retained unchanged (grace window / terminal / post-obs).
     * @param offScreen orders retained unchanged (unanchored, no confirmed slot).
     * @param unchanged in-common (state-identical) orders — carried through with no event.
     *                  Pure slot-reanchor orders — both a confirmed reposition and a
     *                  disproven-claim demotion to {@link OrderSlotPosition.OffScreen} —
     *                  arrive as {@link OrderDelta.Reanchor} entries in {@code deltas}
     *                  instead, so their updated {@link OrderSlotPosition} is still
     *                  captured even though they fire no event.
     * @param mutation  pre-composed book mutation for this product — applied here.
     */
    protected final StagedCommit stageCommit(
            String productId,
            BookMutation mutation,
            List<OrderDelta> deltas,
            List<Order> preserved,
            List<Order> offScreen,
            List<Order> unchanged,
            BazaarDataOrigin.OrdersScreen origin) {

        boolean bookChanged = mutation.apply(productId, origin);

        // • preserved — grace / terminal / post-observation retains
        // • offScreen — unanchored retains
        // • unchanged — in-common (no-delta) orders only
        // • deltas    — Place (new), Evict (cancelled / auto-claimed), Update,
        //               PriceCorrection, Reanchor (confirmed reposition or disproven-claim
        //               demotion to OffScreen)
        var next = new ArrayList<Order>(preserved.size() + offScreen.size() + unchanged.size() + deltas.size());
        next.addAll(preserved);
        next.addAll(offScreen);
        next.addAll(unchanged);

        var events = new ArrayList<UserOrderEvent>();

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

        if (!deltas.isEmpty() && NotificationType.ORDERDATA.isEnabled()) {
            long placed = deltas.stream().filter(it -> it instanceof OrderDelta.Place).count();
            long evicted = deltas.stream().filter(it -> it instanceof OrderDelta.Evict).count();
            long updated = deltas.stream().filter(it -> it instanceof OrderDelta.Update).count();
            long expired = deltas.stream().filter(it -> it instanceof OrderDelta.Update u && u.kind() == OrderDelta.Update.UpdateKind.EXPIRY).count();
            long priceCorrected = deltas.stream().filter(it -> it instanceof OrderDelta.PriceCorrection).count();
            long reanchored = deltas.stream().filter(it -> it instanceof OrderDelta.Reanchor).count();

            PlayerActionUtil.notifyAll("%s — %s staged: Δ%d placed, Δ%d evicted, Δ%d updated, Δ%d expired, Δ%d price-corrected, Δ%d reanchored".formatted(
                    origin.describe(), productId, placed, evicted, updated, expired, priceCorrected, reanchored), NotificationType.ORDERDATA);
        }

        return new StagedCommit(productId, next, events, !deltas.isEmpty() || bookChanged);
    }

    /**
     * Writes every {@link #stageCommit} result from one screen load in a single atomic
     * storage write, then fires each product's events and posts
     * {@link BazaarDataUpdateEvent} for every product where {@link StagedCommit#changed()}
     * is {@code true}. No-ops entirely if nothing changed for any product.
     *
     * <p>The write has to be batched, not just deferred. {@link UserOrdersStorage#apply}
     * indexes live orders by container slot — every live order's {@link OrderSlotPosition}
     * must be unique — over the <em>entire</em> tracked-order list, on every call. A
     * product's own Phase 3a (see {@code OrdersScreenDataSource#reconcileProduct}) is what
     * guarantees no two orders can still collide by the time they get here — but that
     * guarantee is only as good as the picture Phase 3a resolved against. Writing one
     * product's corrected list before another product's own correction had itself been
     * produced would reopen exactly this gap. Waiting for every product to finish, then
     * folding their combined result into one write, removes that gap entirely: every
     * claim reaching storage already passed through its own owning product's Phase 3a.
     */
    protected final void commitAll(List<StagedCommit> staged, BazaarDataOrigin.OrdersScreen origin) {
        if (staged.stream().noneMatch(StagedCommit::changed)) return;

        var next = new ArrayList<Order>(staged.stream().mapToInt(commit -> commit.orders().size()).sum());
        staged.forEach(commit -> next.addAll(commit.orders()));

        UserOrdersStorage.apply(ignored -> next);

        staged.forEach(commit -> commit.events().forEach(event -> event.post(BazaarUtils.EVENT_BUS)));

        staged.stream()
                .filter(StagedCommit::changed)
                .forEach(commit -> new BazaarDataUpdateEvent(commit.productId(), origin).post(BazaarUtils.EVENT_BUS));
    }

    private static List<UserOrderEvent> getMutationEvents(Order before, Order after) {
        var result = new ArrayList<UserOrderEvent>(List.of());

        if (after.isExpired() && !before.isExpired()) {
            result.add(new UserOrderEvent.Expired(after));

            return result;
        }

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