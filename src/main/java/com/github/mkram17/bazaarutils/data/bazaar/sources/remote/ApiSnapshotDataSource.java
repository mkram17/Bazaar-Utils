package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
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
 * Applies a full Hypixel API snapshot across every product it covers, then infers fill
 * progress for every known position on every touched product.
 *
 * <p>Two passes: the first splices every product's levels once, with no profile
 * dimension at all. The second iterates every known profile
 * ({@link UserOrdersStorage#allKnown}) and, for each, accumulates inferences across
 * every product touched this tick before a single {@link FillInference#applyAll} call
 * — one storage write per profile, not one per (profile, product) pair.
 *
 * <p>Deliberately does not use {@link #processProduct} for this: that method's own
 * per-product loop calls {@code applyAll} once per known profile for that one product
 * alone, so using it across many products in a tick would cost one write per
 * (profile, product) pair instead of one per profile.
 */
@DataSource
public final class ApiSnapshotDataSource extends SnapshotSource {
    public ApiSnapshotDataSource() {}

    /** One product's pass-1 outcome, carried into pass 2 for every profile to consult. */
    private record Touched(String productId, ProductData data, boolean bookChanged, boolean hasHadPriorSnapshot) {}

    /**
     * Splices every product's levels once, with no profile dimension, then — for every
     * known profile, accumulating across every product touched this tick — infers and
     * applies fill progress in a single write per profile.
     *
     * <p>{@code hasHadPriorSnapshot} is captured before this product's own splice runs,
     * so it reflects whether a snapshot ever landed before this one, not this one itself.
     */
    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());

        var sessionStart = TimeUtil.getModInitTime().toInstant().toEpochMilli();

        var touched = new ArrayList<Touched>();

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();

            var data = BazaarDataRegistry.getOrCreate(productId);

            boolean hasHadPriorSnapshot = data.hasReceivedSnapshot();
            boolean bookChanged = splice(productId, entry.getValue().asksLevels(), entry.getValue().bidsLevels(), origin);

            data.markSnapshot(event.getTimestamp());

            touched.add(new Touched(productId, data, bookChanged, hasHadPriorSnapshot));
        }

        var changed = new HashSet<String>();

        for (var entry : UserOrdersStorage.allKnown().entrySet()) {
            var key = entry.getKey();
            var profile = entry.getValue();

            var allInferences = new ArrayList<OrderDelta.Update<BazaarDataOrigin>>();

            for (var t : touched) {
                if (t.bookChanged()) changed.add(t.productId());
                if (!t.hasHadPriorSnapshot()) continue;

                allInferences.addAll(inferForPosition(t.productId(), t.data(), profile,
                        orders -> eligibleOrders(t.productId(), t.data(), orders, sessionStart), origin, NotificationType.ORDERDATA));
            }

            if (FillInference.applyAll(key, allInferences, origin)) {
                allInferences.stream().map(u -> u.before().productId()).forEach(changed::add);
            }
        }

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(Collections.unmodifiableSet(changed), origin).post(BazaarUtils.EVENT_BUS);

            PlayerActionUtil.notifyAll("%s — %d products changed".formatted(origin.describe(), changed.size()), NotificationType.BAZAARDATA);
        }
    }

    /**
     * Orders on {@code productId} eligible for fill inference this tick: either placed
     * or last touched this session, so their stored numbers can be trusted as a
     * baseline, or already missing from the tradable book entirely — unconditional
     * evidence regardless of session history — and, either way, at a price this
     * snapshot actually covered.
     */
    private static List<Order> eligibleOrders(String productId, ProductData data, List<Order> sessionOrders, long sessionStart) {
        return sessionOrders.stream()
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.placedAt() >= sessionStart || order.lastUpdatedAt() >= sessionStart
                        || data.tradableLevels(TransactionType.of(order.side(), TransactionType.Method.ORDER)).get(order.pricePerItem()) == null)
                .filter(order -> data.isPriceWithinCoverage(TransactionType.of(order.side(), TransactionType.Method.ORDER), order.pricePerItem()))
                .toList();
    }
}