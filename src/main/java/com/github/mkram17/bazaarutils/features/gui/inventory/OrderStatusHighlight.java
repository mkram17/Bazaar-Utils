package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
public class OrderStatusHighlight extends BUListener implements ToggleableFeature, SlotHighlight {
    public static final Identifier IDENTIFIER = Identifier.tryBuild(BazaarUtils.MOD_ID, "highlights/standard_background");

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    private static final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Component>> tooltipCache = new ConcurrentHashMap<>();

    private void populateCache(ItemStack stack, AbstractContainerScreen<?> screen) {
        int index = getSlotIndex(stack, screen);
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

    public OrderStatusHighlight() {
        super();
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInitialized);
        ItemTooltipCallback.EVENT.register(this::onTooltip);
    }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent event) {
        if (!BazaarUtilsModules.OrderStatusHighlight.isEnabled() || !ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE)) {
            return;
        }

        AbstractContainerScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class).orElse(null);
        if (screen == null) return;

        event.getItemStacks().forEach(stack -> populateCache(stack, screen));
    }

    private void onScreenInitialized(Minecraft client, Screen screen, int width, int height) {
        colorCache.clear();
        tooltipCache.clear();
    }

    private void onTooltip(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, TooltipFlag type, List<Component> lines) {
        if (!isEnabled() || !ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE)) return;

        AbstractContainerScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class).orElse(null);
        if (screen == null) return;

        int index = getSlotIndex(stack, screen);
        if (index == -1) return;

        List<Component> cached = tooltipCache.get(index);
        if (cached != null) lines.addAll(1, cached);
    }

    private static int getSlotIndex(ItemStack stack, AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
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