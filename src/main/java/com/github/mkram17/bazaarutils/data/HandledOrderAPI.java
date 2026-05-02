package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.IgnoreFiller;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.Optional;

/**
 * Captures the order and intended action from the most recent Orders page slot click.
 */
@Module
public final class HandledOrderAPI extends BUListener {
    private static final BazaarLogger LOG = BazaarLogger.of(HandledOrderAPI.class);

    public enum OrderClick {
        OPTIONS,
        CLAIM
    }

    public record Selection(Order order, OrderClick action) {}

    @Nullable
    private static Selection selection = null;

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    @IgnoreFiller
    public void onSlotClick(SlotClickEvent event) {
        if (event.isInPlayerInventory() || event.isCancelled()) return;

        Order order = RenderedOrdersIndex.get(event.getSlot().getContainerSlot()).orElse(null);

        if (order == null) {
            LOG.debug("Current order selected cleared — no order at clicked slot");
            clear();

            return;
        }

        OrderClick action = resolveAction(order, event.getButton());

        if (action == null) {
            LOG.warn("Current order selected cleared — unresolvable intent | side={} claimable={} filled={} button={}", order.side(), order.isClaimable(), order.isFilled(), event.getButton());
            clear();

            return;
        }

        selection = new Selection(order, action);
        LOG.debug("Order captured — action=%s: %s".formatted(action, order.describe()));
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    public void onOrdersPageOpen(ContainerLoadedEvent ignored) {
        LOG.debug("Current order selected cleared — orders page re-opened");
        clear();
    }

    /**
     * Maps click behaviour to action per tested semantics:
     *
     * <pre>
     * SELL / BUY, !claimable → any click → OPTIONS
     * SELL / BUY (Partial), claimable → any click   → CLAIM
     * BUY (Filled), claimable → left(0) → CLAIM
     * BUY (Filled), claimable → right(1) → OPTIONS (flip)
     * </pre>
     *
     * Returns {@code null} for clicks that produce no actionable intent
     */
    @Nullable
    private static HandledOrderAPI.OrderClick resolveAction(Order order, int button) {
        if (!order.isClaimable()) {
            // No unclaimed fill — options menu is the only destination
            return order.isSelectable() ? OrderClick.OPTIONS : null;
        }

        // BUY + fully filled (Filled status): button disambiguates
        if (order.isBuyOrder() && order.isFilled()) {
            return button == 1 ? OrderClick.OPTIONS : OrderClick.CLAIM;
        }

        // Everything else with claimable fill goes straight to claim
        return OrderClick.CLAIM;
    }

    public static Optional<Selection> get() {
        return Optional.ofNullable(selection);
    }

    public static Optional<Order> getForOptions() {
        return get().filter(s -> s.action() == OrderClick.OPTIONS).map(Selection::order);
    }

    public static Optional<Order> getForClaim() {
        return get().filter(s -> s.action() == OrderClick.CLAIM).map(Selection::order);
    }

    public static Optional<OrderInfo> getAsInfo() {
        return get().map(Selection::order).flatMap(OrderInfo::of);
    }

    private static void clear() {
        selection = null;
    }
}