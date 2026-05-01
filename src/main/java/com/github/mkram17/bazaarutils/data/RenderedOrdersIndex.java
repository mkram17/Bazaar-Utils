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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * O(1) slot → order index for the currently or most-recently rendered Orders page.
 *
 * <p>Two write paths, exclusive by the {@code open} flag in {@link RenderedState}:
 * {@link #update} stamps the confirmed per-slot layout from one reconciliation tick and
 * marks the index open; {@link #refresh} rebuilds from the committed order list but is a
 * no-op while open, preventing a storage-derived rebuild from regressing reconciliation
 * data within the same tick.
 *
 * <p>The {@code open} flag is cleared — slot map kept — whenever the
 * player leaves the Orders page: loading a different Bazaar screen
 * ({@link #onNonOrders}) or closing the container entirely
 * ({@link #onContainerClosed}). Either re-enables {@link #refresh} so
 * subsequent storage commits keep the index current while the player is
 * elsewhere.
 */
@Module
public final class RenderedOrdersIndex extends BUListener implements ScreenConstrained {

    private static final AtomicLong GENERATION = new AtomicLong();

    /** Bumped every time the slot → order mapping actually changes */
    public static long generation() {
        return GENERATION.get();
    }

    /**
     * Window of index state. {@code open} is {@code true} after {@link #update} has
     * stamped confirmed reconciliation data; {@code false} permits {@link #refresh} to
     * rebuild from the committed order list.
     *
     * @param bySlot slot index → order for the currently rendered Orders page
     * @param orders flat order list that fed {@link #bySlot}; may include orders that
     *               are not yet visible
     * @param key    the profile the state was captured under; informational only, never
     *               used for lookups. {@code null} in the zero state.
     */
    private record RenderedState(boolean open, @NotNull Map<Integer, Order> bySlot, @NotNull List<Order> orders, @Nullable ProfileKey key) {
        /** Returns the zero-state: not open, empty slot map, empty orders, no key. */
        static RenderedState closed() {
            return new RenderedState(false, Map.of(), List.of(), null);
        }
    }

    private static final AtomicReference<RenderedState> STATE = new AtomicReference<>(RenderedState.closed());

    /**
     * Stamps the confirmed per-slot layout from one Orders page reconciliation and marks
     * the index open. Always writes; never guarded against the current state.
     */
    public static void update(Map<Integer, Order> onScreenBySlot, List<Order> orders, ProfileKey key) {
        STATE.set(new RenderedState(true, Map.copyOf(onScreenBySlot), List.copyOf(orders), key));
        GENERATION.incrementAndGet();

        Util.logMessage("RenderedOrdersIndex: %d slots confirmed".formatted(onScreenBySlot.size()));
    }

    /**
     * Rebuilds the slot map from {@code orders}, retaining only live orders with a visible
     * slot position. A no-op while the index is open — {@link #update} has already stamped
     * authoritative reconciliation data for this tick and rebuilding from storage would
     * regress it.
     */
    public static void refresh(List<Order> orders, ProfileKey key) {
        STATE.updateAndGet(current -> {
            if (current.open()) return current;

            var bySlot = orders.stream()
                    .filter(order -> order.slotPosition().isVisible())
                    .collect(Collectors.toUnmodifiableMap(
                            order -> order.slotPosition().indexIfVisible().getAsInt(),
                            order -> order));

            GENERATION.incrementAndGet();

            return new RenderedState(false, bySlot, List.copyOf(orders), key);
        });
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

    private void clearOpen() {
        STATE.updateAndGet(current -> new RenderedState(false, current.bySlot(), current.orders(), current.key()));
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
        clearOpen();

        var key = ProfileKey.requireProfile("RenderedOrdersIndex"); if (key == null) return;

        refresh(UserOrdersStorage.orders(key), key);
    }
}