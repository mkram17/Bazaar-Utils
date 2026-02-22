package com.github.mkram17.bazaarutils.features.chat;

import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import lombok.Getter;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.Arrays;

@Module
public class UselessBazaarNotificationsRemover extends BUListener implements BUToggleableFeature {
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
        public Text getTooltip() {
            return Text.of(getMessage());
        }
    }

    public boolean isEnabled() {
        return ChatConfig.USELESS_BAZAAR_NOTIFICATIONS_REMOVER_TOGGLE;
    }

    public TransientBazaarMessages[] getExcludedNotifications() {
        return ChatConfig.USELESS_BAZAAR_NOTIFICATIONS_REMOVER_EXCLUDED_NOTIFICATIONS;
    }

    //    We need to consider whether we store this to a DataStorage interface or just keep it to a per-boot level
    public boolean firstTimeRemoved = true;

    public UselessBazaarNotificationsRemover() {}

    private void registerUselessNotificationDetector(){
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!isEnabled()) {
                return true;
            }

            if (isNotificationUseless(message.getString())) {
                if (firstTimeRemoved) {
                    firstTimeRemoved = false;

                    Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll("TIP - Useless Bazaar notifications such as \"Putting goods in escrow...\" are removed by default! " +
                            "To disable this feature, uncheck the \"Remove Useless Bazaar Notifications\" option in the Bazaar Utils settings."));
                }

                return false;
            }

            return true;
        });
    }

    private boolean isNotificationUseless(String message) {
        return Arrays.stream(getExcludedNotifications()).anyMatch(n -> message.contains(n.getMessage()));
    }

    @Override
    protected void registerFabricEvents() {
        super.subscribeToMeteorEventBus = false;
        registerUselessNotificationDetector();
    }
}
