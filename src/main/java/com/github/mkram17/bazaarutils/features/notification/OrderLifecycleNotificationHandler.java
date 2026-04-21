package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
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
 * Consumes every {@link UserOrderEvent} subtype and dispatches a notification
 * for each via its own config entry.
 */
@Module
public final class OrderLifecycleNotificationHandler extends BUListener {
    public static float DEFAULT_VOLUME = 0.2f;
    public static Holder<SoundEvent> DEFAULT_SOUND = SoundEvents.UI_BUTTON_CLICK;

    public OrderLifecycleNotificationHandler() {}

    @Subscription
    public void onOrderPartiallyFilled(UserOrderEvent.PartiallyFilled event) {
        var order = event.getOrder();

        dispatch(NotificationsConfig.ORDER_NOTIFICATIONS_PARTIALLY_FILLED, buildPartiallyFilledMessage(order), order);
    }

    @Subscription
    public void onOrderFilled(UserOrderEvent.Filled event) {
        var order = event.getOrder();

        dispatch(NotificationsConfig.ORDER_NOTIFICATIONS_FILLED, buildFilledMessage(order), order);
    }

    private static MutableComponent buildPartiallyFilledMessage(Order order) {
        int filled = order.filledAmount();
        int total  = order.originalAmount();
        return buildPrefix(order)
                .append(Component.literal(String.format(" is %d/%d filled.", filled, total))
                        .withStyle(ChatFormatting.YELLOW));
    }

    private static MutableComponent buildFilledMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" has been filled!").withStyle(ChatFormatting.GREEN));
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
            PlayerActionUtil.notifyAll(message);
        }

        if (settings.emitClientSound) {
            SoundUtil.playSound(DEFAULT_SOUND, DEFAULT_VOLUME);
        }

        String name = ResourceManager.getProductIdtoNameCache().getOrDefault(order.productId(), "");

        settings.chatCommand.run(name);
    }
}