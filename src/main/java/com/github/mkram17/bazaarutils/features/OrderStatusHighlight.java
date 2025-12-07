package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.SlotHighlightCache;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoUtil;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

//drawing done in MixinHandledScreen
public class OrderStatusHighlight implements BUListener {
    @Getter @Setter
    private boolean enabled;
    public static final Identifier IDENTIFIER = Identifier.tryParse("bazaarutils", "orderstatushighlight/background");
    public static final float BACKGROUND_TRANSPARENCY = 0.9f;

    public OrderStatusHighlight(boolean enabled){
        this.enabled = enabled;
    }

    public static BazaarOrder getHighlightedOrder(int slotIndex) {
        var order = OrderInfoUtil.getUserOrderFromIndex(slotIndex);
        return order.filter(bazaarOrder -> bazaarOrder.getFillStatus() != null && bazaarOrder.getFillStatus() == BazaarOrder.Statuses.SET).orElse(null);
    }

    @Override
    public void subscribe() {
        registerTooltipListener();
        BazaarUtils.EVENT_BUS.subscribe(this);
    }

    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Order Status Highlight"))
                .description(OptionDescription.of(Text.literal("Adds a colored background and tooltip for orders that are competitive, matched or outbid in the orders gui inside the bazaar. For outdated orders, also adds the market price in the tooltip.")))
                .binding(false,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    public void updateHighlightCache(List<ItemStack> itemStacks){
        if (!enabled) return;
        for(ItemStack stack : itemStacks) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
                continue;
            }

            int index = -1;
            for (Slot slot : handledScreen.getScreenHandler().slots) {
                if (!slot.hasStack() || !slot.getStack().equals(stack))
                    continue;
                index = slot.getIndex();
            }

            if (index == -1)
                continue;
            SlotHighlightCache.orderStatusHighlightCache.computeIfAbsent(index, this::getHighlightColorFromIndex);
        }
    }

    private Integer getHighlightColorFromIndex(int index){
        BazaarOrder order = getHighlightedOrder(index);
        if (order == null) {
            return null;
        }

        OrderInfoContainer.Statuses orderStatus = order.getOutbidStatus();
        if (orderStatus == null) return null;
        return getArgbFromOutbidStatus(orderStatus);
    }

    //maybe could be split into separate methods, but this is fine for now
    private void registerTooltipListener() {
        ItemTooltipCallback.EVENT.register((ItemStack stack, net.minecraft.item.Item.TooltipContext context, TooltipType type, List<Text> lines) -> {
            if (!enabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
                return;
            }

            int index = -1;
            for (Slot slot : handledScreen.getScreenHandler().slots) {
                if (!slot.hasStack() || !(slot.getStack().equals(stack)))
                    continue;
                index = slot.getIndex();
            }

            if(!SlotHighlightCache.orderStatusHighlightCache.containsKey(index)) return;

            BazaarOrder order = getHighlightedOrder(index);
            if (order == null) {
                return;
            }

            OrderInfoContainer.Statuses orderStatus = order.getOutbidStatus();
            if(orderStatus == null) return;

            switch (orderStatus) {
                case OUTBID:
                    lines.add(1, Text.literal("OUTBID").formatted(Formatting.RED, Formatting.BOLD));
                    lines.add(2, Text.literal("Market Price: " + Util.getPrettyString(order.getMarketPrice(order.getPriceType()))).formatted(Formatting.RED));
                    break;
                case COMPETITIVE:
                    lines.add(1, Text.literal("COMPETITIVE").formatted(Formatting.GREEN, Formatting.BOLD));
                    break;
                case MATCHED:
                    lines.add(1, Text.literal("MATCHED").formatted(Formatting.YELLOW, Formatting.BOLD));
                    break;
            }
            if(BUConfig.get().developerMode) {
                var sellPrice = order.getMarketPrice(PriceInfoContainer.PriceType.INSTASELL);
                var buyPrice = order.getMarketPrice(PriceInfoContainer.PriceType.INSTABUY);
                if(sellPrice == null || buyPrice == null)
                    return;

                lines.add(Text.literal("[BU] Buy: " + Util.getPrettyString(sellPrice) + " coins"));
                lines.add(Text.literal("[BU] Sell: " + Util.getPrettyString(buyPrice) + " coins"));
            }
        });
    }

    private static int getArgbFromOutbidStatus(OrderInfoContainer.Statuses outbidStatus){
        int color;
        final float r, g, b;

        if (outbidStatus == OrderInfoContainer.Statuses.COMPETITIVE) {
            r = 0.0f; g = 1.0f; b = 0.0f; // Green
        } else if (outbidStatus == OrderInfoContainer.Statuses.OUTBID) {
            r = 1.0f; g = 0.0f; b = 0.0f; // Red
        } else { // MATCHED
            r = 1.0f; g = 1.0f; b = 0.0f; // Yellow
        }
        color = ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, r, g, b);
        return color;
    }
}
