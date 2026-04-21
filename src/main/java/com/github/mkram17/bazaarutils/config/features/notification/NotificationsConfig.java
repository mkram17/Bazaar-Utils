package com.github.mkram17.bazaarutils.config.features.notification;

import com.github.mkram17.bazaarutils.utils.bazaar.BazaarChatCommand;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

@Category(value = "notifications_config")
@ConfigInfo(
        title = "Notifications Config",
        titleTranslation = "bazaarutils.config.notifications.category.label",
        description = "Configurations for the notifications features of the mod",
        descriptionTranslation = "bazaarutils.config.notifications.category.hint",
        icon = "bell"
)
public class NotificationsConfig {

    @ConfigEntry(
            id = "order_notifications",
            translation = "bazaarutils.config.notifications.order_notifications.label"
    )
    @Comment(
            value = "Enables functions to produce different types of notifications, related to the status of your orders.",
            translation = "bazaarutils.config.notifications.order_notifications.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.notifications.separator.order_notifications.label")
    public static boolean ORDER_NOTIFICATIONS_TOGGLE = false;

    @ConfigEntry(
            id = "order_notifications:outbid",
            translation = "bazaarutils.config.notifications.order_notifications.outbid.label"
    )
    @Comment(
            value = "Configure the notification to be produced when an order/offer of yours is outbidded.",
            translation = "bazaarutils.config.notifications.order_notifications.outbid.hint"
    )
    public static final NotificationSettings ORDER_NOTIFICATIONS_OUTBID = new NotificationSettings(false, BazaarChatCommand.NONE, true, false);

    @ConfigEntry(
            id = "order_notifications:filled",
            translation = "bazaarutils.config.notifications.order_notifications.filled.label"
    )
    @Comment(
            value = "Configure the notification to be produced when an order/offer of yours is filled.",
            translation = "bazaarutils.config.notifications.order_notifications.filled.hint"
    )
    public static final NotificationSettings ORDER_NOTIFICATIONS_FILLED = new NotificationSettings(false, BazaarChatCommand.NONE, true, false);

    @ConfigObject
    public static final class NotificationSettings {

        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.notifications.notification.enabled.label"
        )
        @Comment(
                value = "Whether the notification will be produced or not",
                translation = "bazaarutils.config.notifications.notification.enabled.hint"
        )
        public boolean enabled;

        @ConfigEntry(
                id = "run_chat_command",
                translation = "bazaarutils.config.notifications.notification.run_chat_command.label"
        )
        @Comment(
                value = "Whether to run a command when the notification is triggered",
                translation = "bazaarutils.config.notifications.notification.run_chat_command.hint"
        )
        public BazaarChatCommand chatCommand;

        @ConfigEntry(
                id = "emit_chat_message",
                translation = "bazaarutils.config.notifications.notification.emit_chat_message.label"
        )
        public boolean emitChatMessage;

        @ConfigEntry(
                id = "emit_client_sound",
                translation = "bazaarutils.config.notifications.notification.emit_client_sound.label"
        )
        public boolean emitClientSound;

        public boolean isEnabled() {
            return enabled && NotificationsConfig.ORDER_NOTIFICATIONS_TOGGLE;
        }

        public NotificationSettings(boolean enabled, BazaarChatCommand chatCommand, boolean emitChatMessage, boolean emitClientSound) {
            this.enabled = enabled;
            this.chatCommand = chatCommand;
            this.emitChatMessage = emitChatMessage;
            this.emitClientSound = emitClientSound;
        }
    }
}