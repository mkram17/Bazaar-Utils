package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Module
public final class JoinMessages extends BUListener {

    private static final Text WELCOME_MESSAGE = Text.literal("Thanks for installing! Use /bu or /bazaarutils help to configure the mod.").formatted(Formatting.GREEN);

    private static final Text DISCORD_MESSAGE = Text.literal("For more help or to report a bug, join the ")
            .formatted(Formatting.GREEN)
            .append(Util.DISCORD_TEXT)
            .append(Text.literal("!").formatted(Formatting.GREEN));

    private final Text updateMessage;

    public JoinMessages() {
        super();

        this.updateMessage = Text.literal(BazaarUtils.getUpdateNotes()).formatted(Formatting.DARK_GREEN);
    }

    @Override
    protected void registerFabricEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (MetadataConfig.IS_FIRST_LOAD) {
                sendFirstLoadMessages();
            } else if (BazaarUtils.updatedMajorVersion) {
                sendMajorUpdateMessages();
            }
        });
    }

    private void sendFirstLoadMessages() {
        Util.tickExecuteLater(40, () -> PlayerActionUtil.notifyAll(WELCOME_MESSAGE));
        Util.tickExecuteLater(100, () -> PlayerActionUtil.notifyAll(DISCORD_MESSAGE));

        MetadataConfig.IS_FIRST_LOAD = false;
        ConfigUtil.scheduleConfigSave();
    }

    private void sendMajorUpdateMessages() {
        Util.tickExecuteLater(40, () -> PlayerActionUtil.notifyAll(updateMessage));
        Util.tickExecuteLater(41, () -> PlayerActionUtil.notifyAll(Util.CHANGELOG));

        BazaarUtils.updatedMajorVersion = false;
    }
}