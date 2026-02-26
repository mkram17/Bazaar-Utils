package com.github.mkram17.bazaarutils.config.features.chat;

import com.github.mkram17.bazaarutils.features.chat.StashMessagesRemover;
import com.github.mkram17.bazaarutils.features.chat.UselessBazaarNotificationsRemover;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

@Category(value = "chat_config")
@ConfigInfo(
        title = "Chat Config",
        titleTranslation = "bazaarutils.config.chat.category.label",
        description = "Configurations for the chat features of the mod",
        descriptionTranslation = "bazaarutils.config.chat.category.hint",
        icon = "keyboard"
)
public final class ChatConfig {

    @ConfigEntry(
            id = "useless_bazaar_notifications_remover",
            translation = "bazaarutils.config.chat.useless_bazaar_notifications_remover.label"
    )
    @Comment(
            value = "Enables to remove the transient messages sent to the chat by the Bazaar when interacting with it.",
            translation = "bazaarutils.config.chat.useless_bazaar_notifications_remover.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.chat.separator.useless_bazaar_notifications_remover.label")
    public static boolean USELESS_BAZAAR_NOTIFICATIONS_REMOVER_TOGGLE = false;

    @ConfigEntry(
            id = "useless_bazaar_notifications_remover:excluded_notifications",
            translation = "bazaarutils.config.chat.useless_bazaar_notifications_remover.excluded_notifications.label"
    )
    @Comment(
            value = "The list of transient messages/notifications to be excluded/unallowed from the chat",
            translation = "bazaarutils.config.chat.useless_bazaar_notifications_remover.excluded_notifications.hint"
    )
    @ConfigOption.Draggable()
    public static UselessBazaarNotificationsRemover.TransientBazaarMessages[] USELESS_BAZAAR_NOTIFICATIONS_REMOVER_EXCLUDED_NOTIFICATIONS = new UselessBazaarNotificationsRemover.TransientBazaarMessages[]{
            UselessBazaarNotificationsRemover.TransientBazaarMessages.CANCELLING_ORDER,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.PUTTING_GOODS_IN_ESCROW,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.SUBMITTING_BUY_ORDER,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.CLAIMING_ORDER,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.SUBMITTING_SELL_OFFER,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.EXECUTING_INSTANT_SELL,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.EXECUTING_INSTANT_BUY,
            UselessBazaarNotificationsRemover.TransientBazaarMessages.CLAIMING_ORDERS,
    };

    @ConfigEntry(
            id = "stash_messages_remover",
            translation = "bazaarutils.config.chat.stash_messages_remover.label"
    )
    @Comment(
            value = "Enables to remove the messages sent to the chat remembering to pick up your stash.",
            translation = "bazaarutils.config.chat.stash_messages_remover.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.chat.separator.stash_messages_remover.label")
    public static boolean STASH_MESSAGES_REMOVER_TOGGLE = false;
}