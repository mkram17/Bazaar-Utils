package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * O(1) slot → order index for the currently or most-recently rendered Orders page.
 *
 * <p>Two write paths, exclusive by the {@code open} flag in {@link RenderedState}:
 * {@link #update} stamps the confirmed per-slot layout from one reconciliation tick and
 * marks the index open; {@link #refresh} rebuilds from the committed order list but is a
 * no-op while open, preventing a storage-derived rebuild from regressing reconciliation
 * data within the same tick. {@link #forceRefresh} bypasses {@code open} on profile
 * switch so stale orders from an outgoing profile never remain indexed.
 *
 * <p>The {@code open} flag is cleared — slot map kept — whenever the
 * player leaves the Orders page: loading a different Bazaar screen
 * ({@link #onNonOrders}) or closing the container while the Orders page was open
 * ({@link #onContainerClosed}).
 */
@Module
public final class RenderedOrdersAPI extends BUListener implements ScreenConstrained {

    /**
     * Atomic snapshot of index state. {@code generation} increments monotonically on every
     * content update, inside the state record itself so generation and slot map are always
     * observed consistently together.
     *
     * @param generation monotonically increasing version counter bumped whenever state changes
     * @param open       {@code true} after {@link #update} has stamped confirmed reconciliation data
     * @param bySlot     slot index → order for the currently rendered Orders page
     * @param orders     flat order list that fed {@link #bySlot}; may include off-screen orders
     * @param key        the profile the state was captured under; {@code null} in the zero state
     */
    private record RenderedState(
            long generation,
            boolean open,
            @NotNull Map<Integer, Order> bySlot,
            @NotNull List<Order> orders,
            @Nullable ProfileKey key
    ) {
        static RenderedState closed() {
            return new RenderedState(0L, false, Map.of(), List.of(), null);
        }
    }

    private static final AtomicReference<RenderedState> STATE = new AtomicReference<>(RenderedState.closed());

    /** Bumped atomically every time the slot → order mapping changes. */
    public static long generation() {
        return STATE.get().generation();
    }

    /**
     * Stamps the confirmed per-slot layout from one Orders page reconciliation and marks
     * the index open. Always writes; never guarded against the current state.
     */
    public static void update(Map<Integer, Order> onScreenBySlot, List<Order> orders, ProfileKey key) {
        Map<Integer, Order> slotCopy = Map.copyOf(onScreenBySlot);
        List<Order> orderCopy = List.copyOf(orders);

        STATE.updateAndGet(current -> new RenderedState(current.generation() + 1L, true, slotCopy, orderCopy, key));

        Util.logMessage("RenderedOrdersAPI: %d slots confirmed".formatted(slotCopy.size()));
    }

    /**
     * Rebuilds the slot map from {@code orders}, retaining only actionable orders with a visible
     * slot position. A no-op while the index is open — {@link #update} has already stamped
     * authoritative reconciliation data for this tick and rebuilding from storage would
     * regress it.
     */
    public static void refresh(List<Order> orders, ProfileKey key) {
        List<Order> orderCopy = List.copyOf(orders);
        Map<Integer, Order> bySlot = indexVisible(orderCopy);

        STATE.updateAndGet(current -> {
            if (current.open()) return current;

            return new RenderedState(current.generation() + 1L, false, bySlot, orderCopy, key);
        });
    }

    /**
     * Unconditionally clears {@code open} and rebuilds the slot map for {@code key}.
     * Used on profile switches so switching away while the Orders screen was open never
     * leaves the outgoing profile's orders indexed.
     */
    public static void forceRefresh(List<Order> orders, ProfileKey key) {
        List<Order> orderCopy = List.copyOf(orders);
        Map<Integer, Order> bySlot = indexVisible(orderCopy);

        STATE.updateAndGet(current -> new RenderedState(current.generation() + 1L, false, bySlot, orderCopy, key));
    }

    private static Map<Integer, Order> indexVisible(List<Order> orders) {
        return orders.stream()
                .filter(Order::isActionable)
                .filter(order -> order.slotPosition().isVisible())
                .collect(Collectors.toUnmodifiableMap(
                        order -> order.slotPosition().indexIfVisible().getAsInt(),
                        order -> order,
                        (existing, replacement) -> {
                            Util.logError("RenderedOrdersAPI: duplicate slot collision between %s and %s"
                                    .formatted(existing.describe(), replacement.describe()), null);
                            return replacement;
                        }
                ));
    }

    /** Returns the order anchored at {@code slot}, or empty if no order occupies it. */
    public static Optional<Order> get(int slot) {
        return Optional.ofNullable(STATE.get().bySlot().get(slot));
    }

    /** Flat list of orders that fed the current slot map. Empty in the zero state. */
    public static List<Order> orders() {
        return STATE.get().orders();
    }

    /** The profile key the current state was captured under. */
    public static Optional<ProfileKey> key() {
        return Optional.ofNullable(STATE.get().key());
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.any().except(BazaarScreenType.ORDERS_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    /** Clears the {@code open} flag and returns whether it was previously {@code true}. */
    private boolean clearOpen() {
        RenderedState previous = STATE.getAndUpdate(current ->
                current.open()
                        ? new RenderedState(current.generation(), false, current.bySlot(), current.orders(), current.key())
                        : current);
        return previous.open();
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onNonOrders(ContainerLoadedEvent ignored) {
        clearOpen();
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    private void onContainerClosed(ContainerCloseEvent ignored) {
        if (!clearOpen()) return;

        ProfileKey.current().ifPresent(key -> refresh(UserOrdersStorage.orders(key), key));
    }
}
