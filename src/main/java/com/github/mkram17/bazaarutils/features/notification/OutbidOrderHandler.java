package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

@Module
public class OutbidOrderHandler implements ToggleableFeature {
    @Override
    public boolean isEnabled() {
        return NotificationsConfig.ORDER_NOTIFICATIONS_OUTBID.isEnabled();
    }

    public OutbidOrderHandler() {}

    public static MutableComponent getOutbidMessage(Order order) {
        return createYourOrderForText(order)
                .append(Component.literal(" is now outdated.").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" Click to open bazaar orders").withStyle(ChatFormatting.GOLD));
    }

    public static MutableComponent getCompetitiveMessage(Order order) {
        return createYourOrderForText(order)
                .append(Component.literal(" is no longer outdated.").withStyle(ChatFormatting.DARK_PURPLE));
    }

    public static MutableComponent getMatchedMessage(Order order) {
        return createYourOrderForText(order)
                .append(Component.literal(" has been matched.").withStyle(ChatFormatting.YELLOW));
    }

    private static MutableComponent createYourOrderForText(Order order) {
        return Component.literal("Your " + order.getTransactionType().getSide().toString().toLowerCase() + " order for ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(order.getVolume().toString() + " ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(order.getName()).withStyle(ChatFormatting.GOLD));
    }

    public static List<Order> getOutbidOrders() {
        return UserOrdersStorage.INSTANCE.get()
                .stream()
                .filter(order -> order.getPricingPosition() == PricingPosition.OUTBID && order.getStatus() != OrderStatus.FILLED)
                .toList();
    }
}
