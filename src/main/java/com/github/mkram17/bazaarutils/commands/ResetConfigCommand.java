package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Restores every setting to its default. The bare command only warns — the reset needs the explicit
 * {@code confirm} argument, which the warning offers as a clickable.
 */
@Command
public final class ResetConfigCommand implements BUCommand {
    private static final String CONFIRM = "confirm";

    @Getter
    public final String commandName = "resetconfig";

    @Getter
    public final Component description = Component.literal("Resets every mod setting to its default.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base
                .executes(this::warn)
                .then(ClientCommandManager.literal(CONFIRM).executes(this::reset));
    }

    private int warn(CommandContext<FabricClientCommandSource> context) {
        MutableComponent message = Component.literal("This resets every Bazaar Utils setting to its default. ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Click here to confirm.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        PlayerActionUtil.notifyChatCommand(message, "bu resetconfig " + CONFIRM);

        return 1;
    }

    private int reset(CommandContext<FabricClientCommandSource> context) {
        ConfigUtil.resetToDefaults();

        PlayerActionUtil.notifyAll(Component.literal("Settings reset to defaults.").withStyle(ChatFormatting.GREEN));

        return 1;
    }
}
