package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Command
public final class HelpCommand implements BUCommand {

    @Getter public final String commandName = "help";
    @Getter public final Component description = Component.literal("Lists all available commands.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(this::execute);
    }

    private int execute(CommandContext<FabricClientCommandSource> context) {
        BazaarUtilsCommand root = BazaarUtilsModules.BazaarUtilsCommand;

        MutableComponent message = Component.literal("BazaarUtils Commands\n").withStyle(ChatFormatting.GOLD);

        message.append(Component.literal("---------------------\n").withStyle(ChatFormatting.DARK_GRAY));
        for (BUCommand command : root.getSubcommands()) {
            appendCommand(message, command, "/bu");
        }
        message.append(Component.literal("---------------------").withStyle(ChatFormatting.DARK_GRAY));

        PlayerActionUtil.notifyAll(message.getString());

        return 1;
    }

    private void appendCommand(MutableComponent message, BUCommand command, String path) {
        String fullPath = path + " " + command.getCommandName();

        message.append(Component.literal(fullPath).withStyle(ChatFormatting.GREEN));
        message.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        message.append(command.getDescription());
        message.append(Component.literal("\n"));

        for (BUCommand sub : command.getSubcommands()) {
            appendCommand(message, sub, fullPath);
        }
    }
}