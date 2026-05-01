package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Tracks per-order {@link PricingPosition.PositionContext} and fires {@link UserOrderPositionEvent} on
 * transitions.
 *
 * <p>Reacts to both single-product and batch book updates so no position change is missed
 * regardless of which source triggered the book mutation. Since a book update carries no
 * notion of which profile is "active," every check runs across every known profile's own
 * orders, not just whichever one happens to be played.
 */
@Module
public final class UserOrderHandler extends BUListener {
    /** Last-known position per open order, per profile — the baseline every new reading is compared against. */
    private final Map<ProfileKey, Map<UUID, PricingPosition.PositionContext>> lastKnown = new HashMap<>();

    public UserOrderHandler() {}

    @Subscription(priority = Priority.HIGHEST)
    public void onDataUpdate(BazaarDataUpdateEvent event) {
        checkAllProfiles(Set.of(event.getProductId()));
    }

    @Subscription(priority = Priority.HIGHEST)
    public void onBatchUpdate(BazaarDataBatchUpdateEvent event) {
        checkAllProfiles(event.getChangedProductIds());
    }

    /** Runs {@link #checkPositions} against every profile {@link UserOrdersStorage} currently knows about. */
    private void checkAllProfiles(Set<String> changeset) {
        UserOrdersStorage.allKnown().forEach((key, storage) -> checkPositions(key, storage, changeset));
    }

    /**
     * Checks every open order in {@code storage} whose product is in {@code changeset},
     * then prunes {@code lastKnown}'s entries for this profile down to orders still open —
     * removing empty profile maps so stale/idle profiles don't accumulate empty buckets.
     */
    private void checkPositions(ProfileKey key, List<Order> storage, Set<String> changeset) {
        List<Order> affected = storage.stream()
                .filter(Order::isOpen)
                .filter(o -> changeset.contains(o.productId()))
                .toList();

        var known = lastKnown.get(key);
        if (affected.isEmpty() && known == null) return;

        if (known == null) {
            known = new HashMap<>();
            lastKnown.put(key, known);
        }

        for (Order order : affected) {
            checkOrder(key, order, storage, known);
        }

        Set<UUID> openIds = storage.stream()
                .filter(Order::isOpen)
                .map(Order::id)
                .collect(Collectors.toSet());
        known.keySet().retainAll(openIds);

        if (known.isEmpty()) {
            lastKnown.remove(key);
        }
    }

    /**
     * Computes the order's current {@link PricingPosition.PositionContext} and fires
     * {@link UserOrderPositionEvent} if it differs from the last known value for this
     * profile.
     */
    private void checkOrder(ProfileKey key, Order order, List<Order> userOrders, Map<UUID, PricingPosition.PositionContext> known) {
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
