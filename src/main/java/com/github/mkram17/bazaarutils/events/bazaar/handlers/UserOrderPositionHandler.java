package com.github.mkram17.bazaarutils.events.bazaar.handlers;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderPositionEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Tracks per-order {@link PricingPosition} and fires {@link UserOrderPositionEvent}
 * on transitions.
 *
 * <p>Reacts to both single-product and batch book updates so no position change
 * is missed regardless of which source triggered the book mutation.
 */
@Module
public final class UserOrderPositionHandler extends BUListener {
    private final Map<UUID, PricingPosition> lastKnown = new HashMap<>();

    public UserOrderPositionHandler() {}

    @Subscription(priority = Subscription.HIGHEST)
    public void onDataUpdate(BazaarDataUpdateEvent event) {
        checkPositions(Set.of(event.getProductId()));
    }

    @Subscription(priority = Subscription.HIGHEST)
    public void onBatchUpdate(BazaarDataBatchUpdateEvent event) {
        checkPositions(event.getChangedProductIds());
    }

    private void checkPositions(Set<String> changeset) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) return;

        storage.stream()
                .filter(order -> changeset.contains(order.productId()))
                .filter(Order::isActive)
                .forEach(order -> checkOrder(order, storage));

        var activeIds = storage.stream().map(Order::id).collect(Collectors.toSet());
        lastKnown.keySet().retainAll(activeIds);
    }

    private void checkOrder(Order order, List<Order> userOrders) {
        var current = order.position(userOrders).orElse(null);
        if (current == null) {
            PlayerLogger.debug("%s — no market data, position check skipped".formatted(order.describe()), NotificationType.ORDER_POSITION);

            return;
        }

        var previous = lastKnown.put(order.id(), current);
        if (current == previous) {
            if (NotificationType.ORDER_POSITION.isEnabled()) {
                PlayerLogger.debug("%s — position unchanged (%s)".formatted(order.describe(), current), NotificationType.ORDER_POSITION);
            }

            return;
        }

        PlayerLogger.debug("%s — transition: %s → %s".formatted(order.describe(), previous, current), NotificationType.ORDER_POSITION);

        new UserOrderPositionEvent(order, previous, current).post(EVENT_BUS);
    }
}