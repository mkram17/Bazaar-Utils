package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderPositionEvent;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Consumes {@link UserOrderPositionEvent} and notifies the player on each position transition.
 */
@Module
public class OutbidOrderHandler extends BUListener implements ToggleableFeature {
    @Override
    public boolean isEnabled() {
        return NotificationsConfig.ORDER_NOTIFICATIONS_OUTBID.isEnabled();
    }

    public OutbidOrderHandler() {}

    @Subscription
    @OnlyWhenEnabled
    public void onPositionChange(UserOrderPositionEvent event) {
        MutableComponent message = switch (event.getPosition()) {
            case OUTBID -> getOutbidMessage(event.getOrder());
            case COMPETITIVE -> getCompetitiveMessage(event.getOrder());
            case MATCHED -> getMatchedMessage(event.getOrder());
        };

        PlayerActionUtil.notifyAll(message);
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private static MutableComponent getOutbidMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" is now outbid.").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" Click to open bazaar orders.").withStyle(ChatFormatting.GOLD));
    }

    private static MutableComponent getCompetitiveMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" is no longer outbid.").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static MutableComponent getMatchedMessage(Order order) {
        return buildPrefix(order)
                .append(Component.literal(" has been matched.").withStyle(ChatFormatting.YELLOW));
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
}