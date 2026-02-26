package com.github.mkram17.bazaarutils.config.features.notification;

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
    public static final NotificationSettings ORDER_NOTIFICATIONS_OUTBID = new NotificationSettings(false, false, true, false);

    @ConfigEntry(
            id = "order_notifications:filled",
            translation = "bazaarutils.config.notifications.order_notifications.filled.label"
    )
    @Comment(
            value = "Configure the notification to be produced when an order/offer of yours is filled.",
            translation = "bazaarutils.config.notifications.order_notifications.filled.hint"
    )
    public static final NotificationSettings ORDER_NOTIFICATIONS_FILLED = new NotificationSettings(false, false, true, false);

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
                id = "auto_open_bazaar",
                translation = "bazaarutils.config.notifications.notification.auto_open_bazaar.label"
        )
        public boolean autoOpenBazaar;

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

        public NotificationSettings(boolean enabled, boolean autoOpenBazaar, boolean emitChatMessage, boolean emitClientSound) {
            this.enabled = enabled;
            this.autoOpenBazaar = autoOpenBazaar;
            this.emitChatMessage = emitChatMessage;
            this.emitClientSound = emitClientSound;
        }
    }
}