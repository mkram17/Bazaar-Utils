package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class OrderStatusHighlight extends BUListener implements BUToggleableFeature, SlotHighlight {
    public static final Identifier IDENTIFIER = Identifier.tryParse(BazaarUtils.MOD_ID, "highlights/standard_background");

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    private static final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Text>> tooltipCache = new ConcurrentHashMap<>();

    private void populateCache(ItemStack stack, HandledScreen<?> screen) {
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

        HandledScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(HandledScreen.class).orElse(null);
        if (screen == null) return;

        event.getItemStacks().forEach(stack -> populateCache(stack, screen));
    }

    private void onScreenInitialized(MinecraftClient client, Screen screen, int width, int height) {
        colorCache.clear();
        tooltipCache.clear();
    }

    private void onTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context, TooltipType type, List<Text> lines) {
        if (!isEnabled() || !ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE)) return;

        HandledScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(HandledScreen.class).orElse(null);
        if (screen == null) return;

        int index = getSlotIndex(stack, screen);
        if (index == -1) return;

        List<Text> cached = tooltipCache.get(index);
        if (cached != null) lines.addAll(1, cached);
    }

    private static int getSlotIndex(ItemStack stack, HandledScreen<?> screen) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.hasStack() && slot.getStack().equals(stack)) return slot.getIndex();
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

    private static List<Text> buildTooltipLines(Order order, PricingPosition pos) {
        List<Text> lines = new ArrayList<>();

        switch (pos) {
            case COMPETITIVE -> lines.add(styledText("COMPETITIVE", InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR, true));
            case MATCHED -> lines.add(styledText("MATCHED", InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR,     true));
            case OUTBID -> {
                lines.add(styledText("OUTBID", InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, true));
                lines.add(styledText("Market Price: " + Util.getPrettyString(order.getMarketPrice(order.getTransactionType().getSide())), InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, false));
            }
        }

        if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
            lines.add(Text.literal("[BU] Buy: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.BUY))  + " coins"));
            lines.add(Text.literal("[BU] Sell: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.SELL)) + " coins"));
        }

        return lines;
    }

    private static Text styledText(String content, int rgb, boolean bold) {
        return Text.literal(content).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));
    }
}