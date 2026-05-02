package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Module
public final class JoinMessages extends BUListener {

    private static final Component WELCOME_MESSAGE = Component.literal("Thanks for installing! Use /bu or /bazaarutils help to configure the mod.").withStyle(ChatFormatting.GREEN);

    private static final Component DISCORD_MESSAGE = Component.literal("For more help or to report a bug, join the ")
            .withStyle(ChatFormatting.GREEN)
            .append(Util.DISCORD_TEXT)
            .append(Component.literal("!").withStyle(ChatFormatting.GREEN));

    private final Component updateMessage;

    public JoinMessages() {
        super();

        this.updateMessage = Component.literal(MetadataConfig.UPDATE_NOTES).withStyle(ChatFormatting.DARK_GREEN);
    }

    @Override
    protected void registerFabricEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (MetadataConfig.IS_FIRST_LOAD) {
                sendFirstLoadMessages();
            } else if (MetadataConfig.SIGNIFICANT_VERSION_UPGRADE) {
                sendSignificantUpdateMessages();
            }
        });
    }

    private void sendFirstLoadMessages() {
        Util.tickExecuteLater(40, () -> PlayerLogger.send(WELCOME_MESSAGE));
        Util.tickExecuteLater(100, () -> PlayerLogger.send(DISCORD_MESSAGE));

        MetadataConfig.IS_FIRST_LOAD = false;
        ConfigUtil.scheduleConfigSave();
    }

    private void sendSignificantUpdateMessages() {
        Util.tickExecuteLater(40, () -> PlayerLogger.send(updateMessage));
        Util.tickExecuteLater(41, () -> PlayerLogger.send(Util.CHANGELOG));

        MetadataConfig.SIGNIFICANT_VERSION_UPGRADE = false;
        ConfigUtil.scheduleConfigSave();
    }
}