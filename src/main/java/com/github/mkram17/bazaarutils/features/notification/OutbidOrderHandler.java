package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
        return Component.literal("Your " + order.side().toString().toLowerCase() + " order for ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(order.originalAmount() + " ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal(order.productId()).withStyle(ChatFormatting.GOLD));
    }
}
