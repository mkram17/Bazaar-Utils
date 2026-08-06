package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

/**
 * Takes one confirming click before claiming an order a co-op member placed, so their goods are not
 * swept up by reflex while clearing your own.
 *
 * <p>Inert outside a co-op: Hypixel only writes the {@code By:} line the authorship check relies on
 * while the profile is in one.
 */
@Module
public class CoopOrderClaimConfirmation extends BUListener implements ToggleableFeature, ScreenConstrained {
    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.ORDERS_PAGE);

    private static final int NOTHING_ARMED = -1;

    /** Container slot the player has already been warned about, so the next click on it goes through. */
    private transient int armedSlot = NOTHING_ARMED;

    @Override
    public boolean isEnabled() {
        return InventoryConfig.COOP_ORDER_CLAIM_CONFIRMATION_TOGGLE;
    }

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    public CoopOrderClaimConfirmation() {
        super();
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> armedSlot = NOTHING_ARMED);
    }

    /**
     * Claiming makes Hypixel resend the page, so every fresh view of it asks again. Deliberately
     * ungated — disarming is cheap and always the safe direction.
     */
    @Subscription
    private void onContainerLoaded(ContainerLoadedEvent event) {
        armedSlot = NOTHING_ARMED;
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onSlotClicked(SlotInteractionEvent event) {
        // Player-inventory container indices (0-35) overlap low chest indices, so they would
        // otherwise match unrelated orders.
        if (event.isInPlayerInventory()) return;

        int slotIndex = event.getSlot().getContainerSlot();

        Order order = OrderUtil.getUserOrderFromIndex(slotIndex)
                .filter(OrderInfo::isCoopOrder)
                .filter(CoopOrderClaimConfirmation::hasUnclaimedGoods)
                .orElse(null);

        if (order == null || armedSlot == slotIndex) return;

        armedSlot = slotIndex;

        PlayerActionUtil.notifyAll(warning(order));
        event.cancel();
    }

    private static boolean hasUnclaimedGoods(Order order) {
        return order.getAmountFilled() > order.getAmountClaimed();
    }

    private static Component warning(Order order) {
        boolean buying = order.getTransactionType().getSide() == TransactionType.Side.BUY;
        String kind = buying ? "buy order" : "sell offer";

        return Component.literal(order.getAuthor()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" placed this ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(kind + " for " + order.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(". Click again to claim it.").withStyle(ChatFormatting.WHITE));
    }
}
