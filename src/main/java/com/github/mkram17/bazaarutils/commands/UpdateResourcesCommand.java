package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.Command;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@Command
public final class UpdateResourcesCommand implements BUCommand {
    @Getter
    public final String commandName = "updateresources";

    @Getter
    public final Component description = Component.literal("Checks for and applies resource updates.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            PlayerActionUtil.notifyAll("Checking for resource updates...");
            ResourceManager.checkForUpdates(true);

            return 1;
        });
    }
}