package com.github.mkram17.bazaarutils.features.chat;

import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;

import java.util.Arrays;

@Module
public class UselessBazaarNotificationsRemover extends BUListener implements ToggleableFeature {
    public enum TransientBazaarMessages implements Translatable, TooltipProvider {
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
        public String getTranslationKey() {
            return "bazaarutils.hypixel.messages.transient_bazaar_notifications." + name().toLowerCase() + ".label";
        }

        @Override
        public Component getTooltip() {
            return Component.nullToEmpty(message);
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

    @Subscription
    @OnlyOnSkyBlock
    @OnlyWhenEnabled
    public void onChat(ChatReceivedEvent.Pre event) {
        String message = event.getText();
        if (!isNotificationUseless(message)) return;

        if (!MetadataConfig.USELESS_NOTIFICATIONS_TIP_SHOWN) {
            MetadataConfig.USELESS_NOTIFICATIONS_TIP_SHOWN = true;
            ConfigUtil.scheduleConfigSave();

            Util.tickExecuteLater(2, () -> PlayerLogger.send(
                    "TIP - Useless Bazaar notifications are removed by default! " +
                            "To disable, uncheck \"Remove Useless Bazaar Notifications\" in Bazaar Utils settings."));
        }

        event.cancel();
    }

    private boolean isNotificationUseless(String message) {
        return Arrays.stream(getExcludedNotifications()).anyMatch(n -> message.contains(n.getMessage()));
    }
}