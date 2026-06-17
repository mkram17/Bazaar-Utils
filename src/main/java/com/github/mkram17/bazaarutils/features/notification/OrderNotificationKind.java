package com.github.mkram17.bazaarutils.features.notification;

import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.network.chat.Component;

public enum OrderNotificationKind implements NotificationKind, Translatable, TooltipProvider {
    // UserOrderPositionEvent subtypes
    COMPETITIVE,
    MATCHED,
    OUTBID,

    // UserOrderEvent subtypes
    PLACED,
    PARTIALLY_FILLED,
    FILLED,
    CLAIMED,
    CANCELLED,
    FLIPPED;

    @Override
    public String getTranslationKey() {
        return "bazaarutils.config.notifications.order.kind." + name().toLowerCase() + ".label";
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("bazaarutils.config.notifications.order.kind." + name().toLowerCase() + ".hint");
    }
}