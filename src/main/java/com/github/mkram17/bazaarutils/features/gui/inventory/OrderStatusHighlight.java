package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//drawing done in MixinHandledScreen
@Module
public class OrderStatusHighlight extends BUListener implements ToggleableFeature, ScreenConstrained, SlotHighlight {
    public static final Identifier IDENTIFIER = Identifier.tryBuild(BazaarUtils.MOD_ID, "highlights/standard_background");

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    private static final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Component>> tooltipCache = new ConcurrentHashMap<>();

    private void populateCache(ItemStack stack, List<Slot> slots) {
        int index = getSlotIndex(stack, slots);
        if (index == -1) return;

        Order order = getOrderForHighlight(index);
        if (order == null) return;

        PricingPosition pos = order.getPricingPosition();
        if (pos == null) return;

        colorCache.put(index, getArgbFromPricingPosition(pos));
        tooltipCache.put(index, buildTooltipLines(order, pos));
    }

    @Override
    public Integer getHighlightColor(int slotIndex) {
        return colorCache.get(slotIndex);
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.ORDERS_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    public OrderStatusHighlight() {
        super();
    }

    // Clear doesnt need to be gated to @OnlyBazaarScreen(any = true) because it's cheap and also
    // avoids the predicate's screen-resolution ordering hazard for ContainerInitializedEvent.
    @Subscription
    private void onScreenInitialized(ContainerInitializedEvent event) {
        colorCache.clear();
        tooltipCache.clear();
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        event.getContainerSlots().stream()
                .map(Slot::getItem)
                .forEach(stack -> populateCache(stack, event.getContainerSlots()));
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItem();
        var lines = event.getTooltip();

        // Enablement and ORDERS_PAGE gating are handled by @OnlyWhenEnabled and
        // @OnlyBazaarScreen(useConstraintsInterface = true); the chain below only resolves the menu.
        ScreenManager.getInstance().current()
                .flatMap(context -> context.as(AbstractContainerScreen.class))
                .map(AbstractContainerScreen::getMenu)
                .ifPresent(screen -> {
                    int index = getSlotIndex(stack, screen.slots);
                    if (index == -1) return;

                    List<Component> cached = tooltipCache.get(index);
                    if (cached != null) lines.addAll(1, cached);
                });
    }

    private static int getSlotIndex(ItemStack stack, List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot.hasItem() && slot.getItem().equals(stack)) return slot.getContainerSlot();
        }

        return -1;
    }

    private static Order getOrderForHighlight(int slotIndex) {
        return OrderUtil.getUserOrderFromIndex(slotIndex)
                .filter(o -> o.getStatus() != null && o.getStatus() == OrderStatus.SET)
                .orElse(null);
    }

    private static int getArgbFromPricingPosition(PricingPosition pricingPosition) {
        return switch (pricingPosition) {
            case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
            case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
            case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
        };
    }

    private static List<Component> buildTooltipLines(Order order, PricingPosition pos) {
        List<Component> lines = new ArrayList<>();

        switch (pos) {
            case COMPETITIVE -> lines.add(styledText("COMPETITIVE", InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR, true));
            case MATCHED -> lines.add(styledText("MATCHED", InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR,     true));
            case OUTBID -> {
                lines.add(styledText("OUTBID", InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, true));
                lines.add(styledText("Market Price: " + Util.getPrettyString(order.getMarketPrice(order.getTransactionType().getSide())), InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, false));
            }
        }

        if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
            lines.add(Component.literal("[BU] Buy: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.BUY))  + " coins"));
            lines.add(Component.literal("[BU] Sell: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.SELL)) + " coins"));
        }

        return lines;
    }

    private static Component styledText(String content, int rgb, boolean bold) {
        return Component.literal(content).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));
    }
}