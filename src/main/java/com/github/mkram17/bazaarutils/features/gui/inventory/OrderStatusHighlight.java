package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.misc.SlotHighlightCache;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

//drawing done in MixinHandledScreen
@Module
public class OrderStatusHighlight extends BUListener implements BUToggleableFeature {
    public static final Identifier IDENTIFIER = Identifier.tryParse("bazaarutils", "orderstatushighlight/background");

    public static boolean isEnabled() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    public OrderStatusHighlight() {}

    public static Order getHighlightedOrder(int slotIndex) {
        Optional<Order> order = OrderInfoUtil.getUserOrderFromIndex(slotIndex);

        return order.filter(
                bazaarOrder -> bazaarOrder.getStatus() != null && bazaarOrder.getStatus() == OrderStatus.SET)
                .orElse(null);
    }

    @Override
    protected void registerFabricEvents() {
        super.subscribeToMeteorEventBus = false;
        registerTooltipListener();
    }

    public static void updateHighlightCache(List<ItemStack> itemStacks) {
        if (!isEnabled()) {
            return;
        }

        for (ItemStack stack : itemStacks) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
                continue;
            }

            int index = -1;

            for (Slot slot : handledScreen.getScreenHandler().slots) {
                if (!slot.hasStack() || !slot.getStack().equals(stack)) {
                    continue;
                }

                index = slot.getIndex();
            }

            if (index == -1) {
                continue;
            }

            SlotHighlightCache.orderStatusHighlightCache.computeIfAbsent(index, OrderStatusHighlight::getHighlightColorFromIndex);
        }
    }

    private static Integer getHighlightColorFromIndex(int index) {
        Order order = getHighlightedOrder(index);

        if (order == null) {
            return null;
        }

        PricingPosition pricingPosition = order.getPricingPosition();

        if (pricingPosition == null) {
            return null;
        }

        return getArgbFromOutbidStatus(pricingPosition);
    }

    //maybe could be split into separate methods, but this is fine for now
    private void registerTooltipListener() {
        ItemTooltipCallback.EVENT.register((ItemStack stack, net.minecraft.item.Item.TooltipContext context, TooltipType type, List<Text> lines) -> {
            if (!isEnabled()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();

            if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
                return;
            }

            int index = -1;

            for (Slot slot : handledScreen.getScreenHandler().slots) {
                if (!slot.hasStack() || !(slot.getStack().equals(stack))) {
                    continue;
                }

                index = slot.getIndex();
            }

            if (!SlotHighlightCache.orderStatusHighlightCache.containsKey(index)) {
                return;
            }

            Order order = getHighlightedOrder(index);

            if (order == null) {
                return;
            }

            PricingPosition pricingPosition = order.getPricingPosition();

            if (pricingPosition == null) {
                return;
            }

            switch (pricingPosition) {
                case COMPETITIVE:
                    lines.add(1, Text.literal("COMPETITIVE")
                            .setStyle(Style.EMPTY
                                    .withColor(TextColor.fromRgb(InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR))
                                    .withBold(true)));
                    break;

                case MATCHED:
                    lines.add(1, Text.literal("MATCHED")
                            .setStyle(Style.EMPTY
                                    .withColor(TextColor.fromRgb(InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR))
                                    .withBold(true)));
                    break;

                case OUTBID:
                    lines.add(1, Text.literal("OUTBID")
                            .setStyle(Style.EMPTY
                                    .withColor(TextColor.fromRgb(InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR))
                                    .withBold(true)));

                    lines.add(2, Text.literal("Market Price: " +
                                    Util.getPrettyString(order.getMarketPrice(order.getOrderType())))
                            .setStyle(Style.EMPTY
                                    .withColor(TextColor.fromRgb(InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR))));
                    break;
            }
            if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                var sellPrice = order.getMarketPrice(OrderType.BUY);
                var buyPrice = order.getMarketPrice(OrderType.SELL);

                lines.add(Text.literal("[BU] Buy: " + Util.getPrettyString(sellPrice) + " coins"));
                lines.add(Text.literal("[BU] Sell: " + Util.getPrettyString(buyPrice) + " coins"));
            }
        });
    }

    private static int getArgbFromOutbidStatus(PricingPosition pricingPosition) {
        return switch (pricingPosition) {
            case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
            case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
            case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
        };
    }
}
