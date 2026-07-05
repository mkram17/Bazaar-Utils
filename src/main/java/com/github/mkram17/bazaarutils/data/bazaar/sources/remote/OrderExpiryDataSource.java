package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
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
 * Detects order expiry by elapsed time, once a minute, and transitions affected orders
 * to {@link OrderStatus.Expired}.
 *
 * <p>Scoped to whichever profile is currently active — this does not scan every known
 * profile the way {@link com.github.mkram17.bazaarutils.events.bazaar.UserOrderHandler}
 * does. An inactive profile's own orders only get expired when the player switches to
 * viewing it, or whenever that profile's Orders screen independently observes the
 * expired lore token on load.
 */
@Module
public final class OrderExpiryDataSource extends BUListener {
    public OrderExpiryDataSource() {}

    /**
     * Scans the active profile's active orders for elapsed
     * {@link Order#effectiveExpiresAt()} values, decrements each one's unfilled volume
     * from the book, then commits every expiry transition in a single atomic storage
     * write.
     *
     * <p>All comparisons use one {@code now} snapshot taken at entry, so the set of
     * orders being expired stays fixed for the rest of the method regardless of how
     * long it takes to run.
     */
    @Subscription
    @OnlyOnSkyBlock
    @TimePassed(duration = "1m")
    public void onTick(TickEvent ignored) {
        var origin = new BazaarDataOrigin.OrderExpired(System.currentTimeMillis());

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        // Only Set/Partial orders can still expire — see Order#isActive.
        var toExpire = storage.stream()
                .filter(Order::isActive)
                .filter(order -> order.effectiveExpiresAt() <= origin.timestamp())
                .toList();

        if (toExpire.isEmpty()) return;

        // Mirrors Hypixel's own removal of each order's unfilled volume from the book.
        // Floors at zero if an API snapshot already cleared this level.
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

        // order.expired(origin) advances lastUpdatedAt to now, alongside the status change.
        var expiredById = toExpire.stream()
                .collect(Collectors.toMap(Order::id, order -> order.expired(origin)));

        var result = UserOrdersStorage.apply(key, current -> current.stream()
                .map(order -> expiredById.getOrDefault(order.id(), order))
                .collect(Collectors.toCollection(ArrayList::new)));

        // ── 3. Events ─────────────────────────────────────────────────────────
        var changedProducts = new HashSet<String>();

        result.stream()
                .filter(order -> expiredById.containsKey(order.id()))
                .forEach(order -> {
                    new UserOrderEvent.Expired(order, key).post(BazaarUtils.EVENT_BUS);
                    changedProducts.add(order.productId());

                    PlayerActionUtil.notifyAll("%s — Expired (time-based): %s".formatted(
                                    origin.describe(), order.describe()),
                            NotificationType.ORDERDATA);
                });

        // One event per distinct product touched, not one per order.
        changedProducts.forEach(productId -> new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS));
    }
}