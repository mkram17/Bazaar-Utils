package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Tracks per-order {@link Order.PositionContext} and fires {@link UserOrderPositionEvent} on
 * transitions.
 *
 * <p>Reacts to both single-product and batch book updates so no position change is missed
 * regardless of which source triggered the book mutation. Since a book update carries no
 * notion of which profile is "active," every check runs across every known profile's own
 * orders, not just whichever one happens to be on played.
 */
@Module
public final class UserOrderHandler extends BUListener {
    /** Last-known position per order, per profile — the baseline every new reading is compared against. */
    private final Map<ProfileKey, Map<UUID, Order.PositionContext>> lastKnown = new HashMap<>();

    public UserOrderHandler() {}

    @Subscription(priority = Subscription.HIGHEST)
    public void onDataUpdate(BazaarDataUpdateEvent event) {
        checkAllProfiles(Set.of(event.getProductId()));
    }

    @Subscription(priority = Subscription.HIGHEST)
    public void onBatchUpdate(BazaarDataBatchUpdateEvent event) {
        checkAllProfiles(event.getChangedProductIds());
    }

    /** Runs {@link #checkPositions} against every profile {@link UserOrdersStorage} currently knows about. */
    private void checkAllProfiles(Set<String> changeset) {
        UserOrdersStorage.allKnown().forEach((key, storage) -> checkPositions(key, storage, changeset));
    }

    /**
     * Checks every active order in {@code storage} whose product is in {@code changeset},
     * then prunes {@code lastKnown}'s entries for this profile down to orders still
     * present — an order that left storage (claimed, cancelled) stops being tracked here too.
     */
    private void checkPositions(ProfileKey key, List<Order> storage, Set<String> changeset) {
        var known = lastKnown.computeIfAbsent(key, k -> new HashMap<>());
        storage.stream().filter(o -> changeset.contains(o.productId())).filter(Order::isActive)
                .forEach(order -> checkOrder(key, order, storage, known));

        known.keySet().retainAll(storage.stream().map(Order::id).collect(Collectors.toSet()));
    }

    /**
     * Computes the order's current {@link Order.PositionContext} and fires
     * {@link UserOrderPositionEvent} if it differs from the last known value for this
     * profile. {@link Map#put} returns the displaced entry, providing the previous
     * reading for the transition without a separate lookup.
     */
    private void checkOrder(ProfileKey key, Order order, List<Order> userOrders, Map<UUID, Order.PositionContext> known) {

        var current = order.positionContext(userOrders).orElse(null);
        if (current == null) {
            PlayerActionUtil.notifyAll("%s — no market data, position check skipped".formatted(order.describe()), NotificationType.ORDERDATA);

            return;
        }

        var previous = known.put(order.id(), current);
        if (Objects.equals(current, previous)) {
            PlayerActionUtil.notifyAll("%s — position unchanged (%s)".formatted(order.describe(), current), NotificationType.ORDERDATA);

            return;
        }

        PlayerActionUtil.notifyAll("%s — transition: %s → %s".formatted(order.describe(), previous, current), NotificationType.ORDERDATA);

        new UserOrderPositionEvent(key, order, current, previous).post(EVENT_BUS);
    }
}