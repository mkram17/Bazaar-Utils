package com.github.mkram17.bazaarutils.features.notification;

import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;

import net.minecraft.network.chat.Component;

/**
 * <ul>
 *   <li>{@link #CHAT}       — {@code PlayerLogger.send} / {@code PlayerLogger.sendWithCommand}</li>
 *   <li>{@link #SCREEN} — {@code PlayerLogger.sendTitle}</li>
 *   <li>{@link #SOUND}      — {@code PlayerLogger.playSound(DEFAULT_SOUND, DEFAULT_VOLUME)}</li>
 *   <li>{@link #OS}         — {@code tinyfd_notifyPopup}: D-Bus/libnotify on Linux, Notification Center on macOS, system tray on Windows</li>
 *   <li>{@link #COMMAND}    — auto-runs the kind's configured {@link com.github.mkram17.bazaarutils.utils.bazaar.BazaarChatCommand}</li>
 *   <li>{@link #REMOTE}     — HTTP POST to the kind's webhookUrl (no-op stub)</li>
 * </ul>
 */
public enum NotificationChannelType implements Translatable, TooltipProvider {
    CHAT,
    SCREEN,
    SOUND,
    OS,
    COMMAND,
    REMOTE;

    @Override
    public String getTranslationKey() {
        return "bazaarutils.config.notifications.channel." + name().toLowerCase() + ".label";
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("bazaarutils.config.notifications.channel." + name().toLowerCase() + ".hint");
    }
}