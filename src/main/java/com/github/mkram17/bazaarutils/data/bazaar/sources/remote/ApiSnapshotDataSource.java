package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;

/**
 * Processes Hypixel API snapshots against the book store and infers fill advances for
 * tracked active orders.
 */
@DataSource
public final class ApiSnapshotDataSource extends SnapshotSource {
    public ApiSnapshotDataSource() {}

    /**
     * Processes one API snapshot: applies all product book levels, accumulates fill inferences
     * across the full product set, and commits them in a single {@link FillInference#applyAll}
     * call. Posts {@link com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent}
     * when at least one product changed.
     */
    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());

        var changed = new HashSet<String>();
        var allInferences = new ArrayList<FillInference.Result>();

        // Snapshot active orders once before the loop so all products in this API tick
        // see the same pre-tick state.
        var sessionOrders = UserOrdersStorage.active();

        var sessionStart = TimeUtil.getModInitTime().toInstant().toEpochMilli();

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();
            var data = BazaarDataRegistry.getOrCreate(productId);

            var asksLevels = entry.getValue().asksLevels();
            var bidLevels = entry.getValue().bidLevels();

            // Read BEFORE snapshotProduct: first snapshot has lastApiUpdateTs == -1 → no inference.
            boolean hasHadPriorSnapshot = data.hasReceivedSnapshot();

            // snapshotProduct evaluates the inferrableOrders supplier post-apply, which is
            // required: isWithinSnapshotWindow() inspects lastEntry() of each book side —
            // a value that only exists after data.apply() has run for both sides.
            var inferences = snapshotProduct(
                    productId, asksLevels, bidLevels,
                    /* inferrableOrders — evaluated post-apply; see class doc for eligibility rules */ postApplyData -> {
                        if (!hasHadPriorSnapshot) return List.of();

                        return sessionOrders.stream()
                                .filter(order -> order.productId().equals(productId))
                                .filter(order -> order.placedAt() >= sessionStart || order.lastUpdatedAt() >= sessionStart || data.bookFor(TransactionType.of(order.side(), TransactionType.Method.ORDER)).get(order.pricePerItem()) == null)
                                .filter(order -> isWithinSnapshotWindow(order, postApplyData))
                                .toList();
                    },
                    NotificationType.ORDERDATA,
                    origin);

            // Stamp AFTER snapshotProduct so the baseline guard is never self-defeating.
            data.markSnapshot(event.getTimestamp());

            if (!inferences.isEmpty()) {
                allInferences.addAll(inferences);
                changed.add(productId);
            }
        }

        // Single applyAll after the full loop — one storage write per API tick.
        boolean fillChanged = FillInference.applyAll(allInferences, origin);

        if (fillChanged) {
            // Invariant: applyAll returns true only when allInferences is non-empty, and the loop
            // above already added every affected product to `changed`. This re-add is always a no-op
            // under current logic; retained as a guard if applyAll semantics change.
            allInferences.stream()
                    .map(it -> it.order().productId())
                    .forEach(changed::add);
        }

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(Collections.unmodifiableSet(changed), origin).post(BazaarUtils.EVENT_BUS);

            PlayerActionUtil.notifyAll("%s — %d products changed".formatted(origin.describe(), changed.size()), NotificationType.BAZAARDATA);
        }
    }

    /**
     * Returns {@code true} only if the order's price falls within the API snapshot's
     * coverage window as reflected in the post-apply book.
     *
     * <p>The API captures the top 30 bid levels (highest prices) and the top 30 ask
     * levels (lowest prices). An order priced beyond the 30th level was never present
     * in the snapshot, so its absence from the book cannot be used to infer a fill.
     *
     * <p>Note: when the book contains fewer than {@link ProductData#API_DEPTH} levels
     * after apply, the snapshot was exhaustive and every order on that side is by
     * definition within the window. The boundary checks below still hold in that case
     * because every tracked order will satisfy {@code price >= floor} (bids) or
     * {@code price <= ceiling} (asks) when those are the outermost levels of a
     * complete book.
     *
     * <p>Must be called after {@code data.apply()} has run for both sides, since the
     * book's {@code lastEntry()} defines the coverage boundary.
     *
     * <p>Book orientation in {@code ProductData}:
     * <ul>
     *   <li>sellBook (bids) — descending — {@code lastEntry()} = lowest of the top 30 bids (floor)</li>
     *   <li>buyBook  (asks) — ascending  — {@code lastEntry()} = highest of the top 30 asks (ceiling)</li>
     * </ul>
     */
    private static boolean isWithinSnapshotWindow(Order order, ProductData data) {
        return switch (order.side()) {
            case BUY -> {
                // If the snapshot captured no bid levels at all, inference is unsafe.
                var bids = data.getBidsBook();
                if (bids.isEmpty()) yield false;
                // Worst covered bid = lowest price of the top 30 = lastEntry of descending map.
                double floor = bids.lastEntry().getKey();
                yield order.pricePerItem() >= floor;
            }
            case SELL -> {
                // If the snapshot captured no ask levels at all, inference is unsafe.
                var asks = data.getAsksBook();
                if (asks.isEmpty()) yield false;
                // Worst covered ask = highest price of the top 30 = lastEntry of ascending map.
                double ceiling = asks.lastEntry().getKey();
                yield order.pricePerItem() <= ceiling;
            }
        };
    }
}