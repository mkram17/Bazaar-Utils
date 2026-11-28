package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures the order and intended action from the most recent Orders page slot click.
 *
 * <p>Consumed by {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver}
 * to bias order matching toward the order the player just clicked, rather than falling
 * back to price or volume approximation. Cleared on every Orders page open and profile
 * switch, so a stale selection can never leak into a later click sequence.
 */
@Module
public final class HandledOrderAPI extends BUListener {
    public enum OrderClick {
        OPTIONS,
        CLAIM
    }

    /** The order and click intent captured from the player's most recent Orders page interaction. */
    public record Selection(Order order, OrderClick action) {}

    private static final AtomicReference<@Nullable Selection> SELECTION = new AtomicReference<>();

    @Subscription(priority = Priority.LOW)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    private void onSlotClick(SlotInteractionEvent event) {
        if (event.isInPlayerInventory() || event.isCancelled()) return;

        ContainerInput actionType = event.getActionType();
        int button = event.getButton();

        if (actionType == ContainerInput.CLONE || actionType == ContainerInput.QUICK_CRAFT) return;
        if ((actionType == ContainerInput.PICKUP || actionType == ContainerInput.QUICK_MOVE) && button != 0 && button != 1) return;

        boolean isRightClick = (actionType == ContainerInput.PICKUP || actionType == ContainerInput.QUICK_MOVE) && button == 1;

        Order order = RenderedOrdersAPI.get(event.getSlot().getContainerSlot()).orElse(null);

        if (order == null) {
            Util.logMessage("Current order selected cleared — no order at clicked slot");
            clear();

            return;
        }

        OrderClick action = resolveAction(order, isRightClick);

        if (action == null) {
            Util.logMessage("Current order selected cleared — unresolvable intent | side=%s claimable=%b filled=%b rightClick=%b actionType=%s button=%d"
                    .formatted(order.side(), order.isClaimable(), order.isFilled(), isRightClick, actionType, button));
            clear();

            return;
        }

        SELECTION.set(new Selection(order, action));
        Util.logMessage("Order captured — action=%s: %s".formatted(action, order.describe()));
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    private void onOrdersPageOpen(ContainerLoadedEvent ignored) {
        Util.logMessage("Current order selected cleared — orders page re-opened");
        clear();
    }

    @Subscription(priority = Priority.HIGH)
    private void onProfileChange(ProfileChangeEvent ignored) {
        clear();
    }

    /**
     * Maps slot interaction behaviour to action per tested semantics:
     *
     * <pre>
     * SELL / BUY, !claimable → any interaction → OPTIONS
     * SELL / BUY (Partial), claimable → any interaction → CLAIM
     * BUY (Filled), claimable → left / non-right-click → CLAIM
     * BUY (Filled), claimable → right-click → OPTIONS (flip)
     * </pre>
     *
     * Returns {@code null} for interactions that produce no actionable intent.
     */
    @Nullable
    private static OrderClick resolveAction(Order order, boolean isRightClick) {
        if (!order.isClaimable()) {
            // No unclaimed fill — options menu is the only destination
            return order.canOpenOptions() ? OrderClick.OPTIONS : null;
        }

        // BUY + fully filled (Filled status): right-click opens options (flip)
        if (order.isBuyOrder() && order.isFilled()) {
            return isRightClick ? OrderClick.OPTIONS : OrderClick.CLAIM;
        }

        // Everything else with claimable fill goes straight to claim
        return OrderClick.CLAIM;
    }

    /** Returns the current captured selection, if any. */
    public static Optional<Selection> get() {
        return Optional.ofNullable(SELECTION.get());
    }

    /** Returns the captured order, only if it was captured with {@link OrderClick#OPTIONS} intent. */
    public static Optional<Order> getForOptions() {
        return get().filter(s -> s.action() == OrderClick.OPTIONS).map(Selection::order);
    }

    /** Returns the captured order, only if it was captured with {@link OrderClick#CLAIM} intent. */
    public static Optional<Order> getForClaim() {
        return get().filter(s -> s.action() == OrderClick.CLAIM).map(Selection::order);
    }

    /** Returns the captured order as an {@link OrderInfo}, if one is captured and its product can still be resolved. */
    public static Optional<OrderInfo> getAsInfo() {
        return get().map(Selection::order).flatMap(OrderInfo::of);
    }

    private static void clear() {
        SELECTION.set(null);
    }
}
