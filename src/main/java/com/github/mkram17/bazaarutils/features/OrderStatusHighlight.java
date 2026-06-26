package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.util.List;

//drawing done in MixinHandledScreen
public class OrderStatusHighlight implements BUListener {
    @Getter @Setter
    private boolean enabled;
    public static final Identifier IDENTIFIER = Identifier.tryBuild("bazaarutils", "orderstatushighlight/background");
    public static final float BACKGROUND_TRANSPARENCY = 0.9f;

    public OrderStatusHighlight(boolean enabled){
        this.enabled = enabled;
    }

    private static List<BazaarOrder> getHighlightedOrders() {
        return BUConfig.get().userOrders.stream()
                .filter(order -> order.getItemInfo() != null
                        && order.getItemInfo().slotIndex() != null
                        && order.getFillStatus() == BazaarOrder.Statuses.SET)
                .toList();
    }

    public static BazaarOrder getHighlightedOrder(int slotIndex) {
        return getHighlightedOrders().stream()
                .filter(order -> order.getItemInfo().slotIndex() == slotIndex)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void subscribe() {
//        registerScreenRenderEvents();
        registerTooltipListener();
    }

    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Order Status Highlight"))
                .description(OptionDescription.of(Component.literal("Adds a colored background and tooltip for orders that are competitive, matched or outbid in the orders gui inside the bazaar. For outdated orders, also adds the market price in the tooltip.")))
                .binding(false,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfigGui::createBooleanController)
                .build();
    }

    //maybe could be split into separate methods, but this is fine for now
    private void registerTooltipListener() {
        ItemTooltipCallback.EVENT.register((ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, TooltipFlag type, List<Component> lines) -> {
            if (!enabled) return;
            ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
            if (stack == null || stack.isEmpty() || stack.getHoverName().getString().contains("GLASS_PANE") || !screenInfo.inMenu(ScreenInfo.BazaarMenuType.ORDER_SCREEN)) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> handledScreen)) {
                return;
            }

            for (Component line : lines) {
                String lineText = line.getString();
                if (lineText.contains("FILLED") || lineText.contains("OUTBID") ||
                        lineText.contains("COMPETITIVE") || lineText.contains("MATCHED")) {
                    // the tooltip is already present, skip processing
                    return;
                }
            }

            int index = -1;
            for (Slot slot : handledScreen.getMenu().slots) {
                if (!slot.hasItem() || !(slot.getItem() == stack))
                    continue;
                index = slot.getContainerSlot();
            }

            if(index == -1)
                return;

            BazaarOrder order = getHighlightedOrder(index);
            if (order == null) {
                return;
            }

            OrderInfoContainer.Statuses orderStatus = order.getOutbidStatus();
            if(orderStatus == null) return;

            switch (orderStatus) {
                case OUTBID:
                    lines.add(1, Component.literal("OUTBID").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    lines.add(2, Component.literal("Market Price: " + Util.getPrettyString(order.getMarketPrice(order.getPriceType()))).withStyle(ChatFormatting.RED));
                    break;
                case COMPETITIVE:
                    lines.add(1, Component.literal("COMPETITIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    break;
                case MATCHED:
                    lines.add(1, Component.literal("MATCHED").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                    break;
            }
            if(BUConfig.get().developerMode) {
                var sellPrice = order.getMarketPrice(PriceInfoContainer.PriceType.INSTASELL);
                var buyPrice = order.getMarketPrice(PriceInfoContainer.PriceType.INSTABUY);
                if(sellPrice == null || buyPrice == null)
                    return;

                lines.add(Component.literal("[BU] Buy: " + Util.getPrettyString(sellPrice) + " coins"));
                lines.add(Component.literal("[BU] Sell: " + Util.getPrettyString(buyPrice) + " coins"));
            }
        });
    }
}

