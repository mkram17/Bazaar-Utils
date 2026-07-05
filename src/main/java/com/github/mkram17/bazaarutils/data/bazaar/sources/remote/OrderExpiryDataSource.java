package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Detects order expiry by time and transitions affected orders to {@link OrderStatus.Expired}.
 */
@Module
public final class OrderExpiryDataSource extends BUListener {
    public OrderExpiryDataSource() {}

    /**
     * Scans all active orders for elapsed {@link Order#effectiveExpiresAt()} values,
     * applies book decrements immediately, then commits all expiry transitions in one
     * atomic storage write. All time comparisons use a single {@code now} snapshot taken
     * at entry so the set of expired orders is stable across the entire handler body.
     */
    @Subscription
    @OnlyOnSkyBlock
    @TimePassed(duration = "1m")
    public void onTick(TickEvent ignored) {
        var storage = UserOrdersStorage.get();
        if (storage == null) return;

        long now = System.currentTimeMillis();
        var origin = new BazaarDataOrigin.OrderExpired(now);

        // Collect all active orders whose expiry time has passed.
        // isActive() = Set || Partial only — Filled, Expired, Claimed, Cancelled excluded.
        var toExpire = storage.stream()
                .filter(Order::isActive)
                .filter(order -> order.effectiveExpiresAt() <= now)
                .toList();

        if (toExpire.isEmpty()) return;

        // ── 1. Book decrements ────────────────────────────────────────────────
        // Hypixel removed each expired order's unfilled volume from their market book
        // at expiry time. Mirror that now. No-op per level if the API snapshot already
        // cleared it; removes the stale optimistic level otherwise.
        for (var order : toExpire) {
            if (order.unfilledAmount() > 0) {
                BookMutation.decrement(
                        TransactionType.of(order.side(), TransactionType.Method.ORDER),
                        order.pricePerItem(),
                        order.unfilledAmount(),
                        true  // terminal — unfilled volume is permanently gone
                ).apply(order.productId(), origin);

                PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (order expired)".formatted(
                                origin.describe(),
                                TransactionType.of(order.side(), TransactionType.Method.ORDER).getPriceType(),
                                order.productId(), order.unfilledAmount(), order.pricePerItem()),
                        NotificationType.BAZAARDATA);
            }
        }

        // ── 2. Atomic storage write ───────────────────────────────────────────
        // lastUpdatedAt is advanced to now (via order.expired(origin)). This prevents
        // OrdersScreenDataSource's eviction grace-period logic from double-processing
        // these orders if the player opens the screen within EVICTION_GRACE_MS after
        // this cron fires.
        var expiredById = toExpire.stream()
                .collect(Collectors.toMap(Order::id, order -> order.expired(origin)));

        var result = UserOrdersStorage.apply(current -> current.stream()
                .map(order -> expiredById.getOrDefault(order.id(), order))
                .collect(Collectors.toCollection(ArrayList::new)));

        // ── 3. Events ─────────────────────────────────────────────────────────
        var changedProducts = new HashSet<String>();

        result.stream()
                .filter(order -> expiredById.containsKey(order.id()))
                .forEach(order -> {
                    new UserOrderEvent.Expired(order).post(BazaarUtils.EVENT_BUS);
                    changedProducts.add(order.productId());

                    PlayerActionUtil.notifyAll("%s — Expired (time-based): %s".formatted(
                                    origin.describe(), order.describe()),
                            NotificationType.ORDERDATA);
                });

        // One BazaarDataUpdateEvent per product — not one per order.
        changedProducts.forEach(productId -> new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS));
    }
}