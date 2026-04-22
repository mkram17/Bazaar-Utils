package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderPositionEvent;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Consumes {@link UserOrderPositionEvent} and dispatches a notification for
 * each position state via its own {@link NotificationsConfig.NotificationSettings}.
 */
@Module
public final class OrderPositionNotificationHandler extends BUListener {
    public static float DEFAULT_VOLUME = 0.2f;
    public static Holder<SoundEvent> DEFAULT_SOUND = SoundEvents.UI_BUTTON_CLICK;

    public OrderPositionNotificationHandler() {}

    @Subscription
    public void onPositionChange(UserOrderPositionEvent event) {
        var settings = switch (event.getPosition()) {
            case COMPETITIVE -> NotificationsConfig.ORDER_NOTIFICATIONS_COMPETITIVE;
            case MATCHED -> NotificationsConfig.ORDER_NOTIFICATIONS_MATCHED;
            case OUTBID -> NotificationsConfig.ORDER_NOTIFICATIONS_OUTBIDDED;
        };

        var message = switch (event.getPosition()) {
            case OUTBID -> buildOutbidMessage(event.getOrder());
            case MATCHED -> buildMatchedMessage(event.getOrder());
            case COMPETITIVE -> buildCompetitiveMessage(event.getOrder());
        };

        dispatch(settings, message, event.getOrder());
    }

    private static MutableComponent buildOutbidMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" has been outbid.").withStyle(ChatFormatting.RED))
                .append(Component.literal(" Click to open bazaar orders.").withStyle(ChatFormatting.GOLD));
    }

    private static MutableComponent buildMatchedMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" is at the top of book, but matched by other orders at the same price.")
                        .withStyle(ChatFormatting.YELLOW));
    }

    private static MutableComponent buildCompetitiveMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" is now fully competitive — no external orders ahead or at price.")
                        .withStyle(ChatFormatting.GREEN));
    }

    private static MutableComponent buildPrefix(Order order) {
        return OrderInfo.of(order)
                .map(info ->
                        Component.literal("Your " + order.side().toString().toLowerCase() + " order for ")
                                .withStyle(ChatFormatting.WHITE)
                                .append(Component.literal(order.originalAmount() + "x ").withStyle(ChatFormatting.DARK_PURPLE))
                                .append(Component.literal(info.getName()).withStyle(ChatFormatting.GOLD)))
                .orElse(Component.empty());
    }

    private void dispatch(NotificationsConfig.NotificationSettings settings, Component message, Order order) {
        if (!settings.isEnabled()) return;

        if (settings.emitChatMessage) {
            PlayerLogger.send(message);
        }

        if (settings.emitClientSound) {
            SoundUtil.playSound(DEFAULT_SOUND, DEFAULT_VOLUME);
        }

        String name = ResourceManager.getProductIdtoNameCache().getOrDefault(order.productId(), "");

        settings.chatCommand.run(name);
    }
}