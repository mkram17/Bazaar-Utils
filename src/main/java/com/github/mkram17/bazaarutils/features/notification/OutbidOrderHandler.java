package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.features.util.ConfigurableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

@Module
public class OutbidOrderHandler implements ConfigurableFeature {
    public static boolean isEnabled() {
        return NotificationsConfig.ORDER_NOTIFICATIONS_OUTBID.isEnabled();
    }

    public OutbidOrderHandler() {}

    public static MutableText getOutbidMessage(Order order) {
        return createYourOrderForText(order)
                .append(Text.literal(" is now outdated.").formatted(Formatting.WHITE))
                .append(Text.literal(" Click to open bazaar orders").formatted(Formatting.GOLD));
    }

    public static MutableText getCompetitiveMessage(Order order) {
        return createYourOrderForText(order)
                .append(Text.literal(" is no longer outdated.").formatted(Formatting.DARK_PURPLE));
    }

    public static MutableText getMatchedMessage(Order order) {
        return createYourOrderForText(order)
                .append(Text.literal(" has been matched.").formatted(Formatting.YELLOW));
    }

    private static MutableText createYourOrderForText(Order order) {
        return Text.literal("Your " + order.getOrderType().getString().toLowerCase() + " order for ").formatted(Formatting.WHITE)
                .append(Text.literal(order.getVolume().toString() + " ").formatted(Formatting.DARK_PURPLE))
                .append(Text.literal(order.getName()).formatted(Formatting.GOLD));
    }

    public static List<Order> getOutbidOrders() {
        return UserOrdersStorage.INSTANCE.get()
                .stream()
                .filter(order -> order.getPricingPosition() == PricingPosition.OUTBID && order.getStatus() != OrderStatus.FILLED)
                .toList();
    }
}
