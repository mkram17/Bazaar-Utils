package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Module
public final class DiscordCommand implements BUCommand {
    @Getter
    public final String commandName = "discord";

    @Getter
    public final Text description = Text.literal("Opens the BazaarUtils Discord invite.").formatted(Formatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();

            client.send(() -> client.setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    try {
                        net.minecraft.util.Util.getOperatingSystem().open(new URI(Util.DISCORD_LINK));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
                MinecraftClient.getInstance().setScreen(null);
            }, Util.DISCORD_LINK, true)));

            return 1;
        });
    }
}