package com.github.mkram17.bazaarutils.data;

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
    /**
     * Snapshot of index state. {@code open} is {@code true} after {@link #update} has
     * stamped confirmed reconciliation data; {@code false} permits {@link #refresh} to
     * rebuild from the committed order list.
     */
    private record RenderedState(boolean open, Map<Integer, Order> bySlot) {
        /** Returns the zero-state: not open, empty slot map. */
        static RenderedState closed() {
            return new RenderedState(false, Map.of());
        }
    }

    private static final AtomicReference<RenderedState> STATE = new AtomicReference<>(RenderedState.closed());

    /**
     * Stamps the confirmed per-slot layout from one Orders page reconciliation and marks
     * the index open. Always writes; never guarded against the current state.
     */
    public static void update(Map<Integer, Order> onScreenBySlot) {
        STATE.set(new RenderedState(true, Map.copyOf(onScreenBySlot)));
        Util.logMessage("RenderedOrdersIndex: %d slots confirmed".formatted(onScreenBySlot.size()));
    }

    /**
     * Rebuilds the slot map from {@code orders}, retaining only live orders with a visible
     * slot position. A no-op while the index is open — {@link #update} has already stamped
     * authoritative reconciliation data for this tick and rebuilding from storage would
     * regress it.
     */
    public static void refresh(List<Order> orders) {
        STATE.updateAndGet(current -> {
            if (current.open()) return current;

            // Was order.isLive() — that now means "has an open market position,"
            // which is unconditionally false once expired. This index has to answer
            // a different question: "is something rendered at this slot that a
            // player could click." An expired order is exactly as clickable as a
            // live one — it's rendered, and it still needs Options.
            var bySlot = orders.stream()
                    .filter(order -> !order.isTerminal() && order.slotPosition().isVisible())
                    .collect(Collectors.toUnmodifiableMap(
                            order -> order.slotPosition().indexIfVisible().getAsInt(),
                            order -> order));

            return new RenderedState(false, bySlot);
        });
    }

    /** Returns the order anchored at {@code slot}, or empty if no order occupies it. */
    public static Optional<Order> get(int slot) {
        return Optional.ofNullable(STATE.get().bySlot().get(slot));
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.any().except(BazaarScreenType.ORDERS_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onNonOrders(ContainerLoadedEvent ignored) {
        STATE.updateAndGet(current -> new RenderedState(false, current.bySlot()));
    }

    /**
     * Clears the {@code open} flag on any container close, the same way
     * {@link #onNonOrders} does on a differently-typed Bazaar screen load.
     * Needed because leaving the Bazaar UI entirely — closing it without
     * opening a different Bazaar screen — produces no
     * {@link ContainerLoadedEvent} at all, and without this the index
     * would otherwise stay pinned open from a reconciliation that is no
     * longer even on screen.
     */
    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    private void onContainerClosed(ContainerCloseEvent ignored) {
        STATE.updateAndGet(current -> new RenderedState(false, current.bySlot()));
    }
}