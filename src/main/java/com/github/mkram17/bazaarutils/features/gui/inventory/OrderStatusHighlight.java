package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

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
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public OrderStatusHighlight() {
        super();
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInitialized);
        ItemTooltipCallback.EVENT.register(this::onTooltip);
    }

    @EventHandler
    private void onContainerLoaded(ContainerLoadedEvent event) {
        if (!isEnabled() || !inCorrectScreen(event)) return;

        event.getContainerSlots().stream()
                .map(Slot::getItem)
                .forEach(stack -> populateCache(stack, event.getContainerSlots()));
    }

    private void onScreenInitialized(Minecraft client, Screen screen, int width, int height) {
        colorCache.clear();
        tooltipCache.clear();
    }

    private void onTooltip(ItemStack stack, Item.TooltipContext tooltip, TooltipFlag type, List<Component> lines) {
        if (!isEnabled()) return;

        ScreenManager.getInstance().current()
                .filter(context -> context.is(BazaarScreenType.ORDERS_PAGE))
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