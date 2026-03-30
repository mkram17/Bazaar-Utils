package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.Command;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Command
public final class DiscordCommand implements BUCommand {
    @Getter
    public final String commandName = "discord";

    @Getter
    public final Component description = Component.literal("Opens the BazaarUtils Discord invite.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            Minecraft client = Minecraft.getInstance();

            client.schedule(() -> client.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    try {
                        net.minecraft.util.Util.getPlatform().openUri(new URI(Util.DISCORD_LINK));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
                Minecraft.getInstance().setScreen(null);
            }, Util.DISCORD_LINK, true)));

            return 1;
        });
    }
}