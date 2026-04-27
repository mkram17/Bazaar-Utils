package com.github.mkram17.bazaarutils.features.chat;

import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;

import java.util.Arrays;

@Module
public class UselessBazaarNotificationsRemover extends BUListener implements ToggleableFeature {
    public enum TransientBazaarMessages implements TooltipProvider {
        CANCELLING_ORDER("[Bazaar] Cancelling order..."),
        PUTTING_GOODS_IN_ESCROW("[Bazaar] Putting goods in escrow..."),
        SUBMITTING_BUY_ORDER("[Bazaar] Submitting buy order..."),
        CLAIMING_ORDER("[Bazaar] Claiming order..."),
        SUBMITTING_SELL_OFFER("[Bazaar] Submitting sell offer..."),
        EXECUTING_INSTANT_SELL("[Bazaar] Executing instant sell..."),
        EXECUTING_INSTANT_BUY("[Bazaar] Executing instant buy..."),
        CLAIMING_ORDERS("[Bazaar] Claiming orders...");

        @Getter
        private final String message;

        TransientBazaarMessages(String message) {
            this.message = message;
        }

        @Override
        public Component getTooltip() {
            return Component.nullToEmpty(getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return ChatConfig.USELESS_BAZAAR_NOTIFICATIONS_REMOVER_TOGGLE;
    }

    public TransientBazaarMessages[] getExcludedNotifications() {
        return ChatConfig.USELESS_BAZAAR_NOTIFICATIONS_REMOVER_EXCLUDED_NOTIFICATIONS;
    }

    public UselessBazaarNotificationsRemover() {}
    //  We need to consider whether we store this to a DataStorage interface or just keep it to a per-boot level
    public transient boolean firstTimeRemoved = true;

    @Subscription
    @OnlyOnSkyBlock
    private void onChat(ChatReceivedEvent.Pre event) {
        if (!isEnabled()) return;

        String message = event.getText();

        if (isNotificationUseless(message)) {
            if (firstTimeRemoved) {
                firstTimeRemoved = false;

                Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll("TIP - Useless Bazaar notifications such as \"Putting goods in escrow...\" are removed by default! " +
                        "To disable this feature, uncheck the \"Remove Useless Bazaar Notifications\" option in the Bazaar Utils settings."));
            }

            event.cancel();
        }
    }

    private boolean isNotificationUseless(String message) {
        return Arrays.stream(getExcludedNotifications()).anyMatch(n -> message.contains(n.getMessage()));
    }
}
