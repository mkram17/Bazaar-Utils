package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

@Module
public final class HelpCommand implements BUCommand {

    @Getter public final String commandName = "help";
    @Getter public final Text description = Text.literal("Lists all available commands.").formatted(Formatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(this::execute);
    }

    private int execute(CommandContext<FabricClientCommandSource> context) {
        List<BUCommand> commands = BazaarUtilsModules.collected.stream()
                .filter(it -> it instanceof BUCommand)
                .map(it -> (BUCommand) it)
                .toList();

        MutableText message = Text.literal("BazaarUtils Commands\n").formatted(Formatting.GOLD);

        message.append(Text.literal("---------------------\n").formatted(Formatting.DARK_GRAY));
        for (BUCommand command : commands) {
            message.append(Text.literal("/bu " + command.getCommandName()).formatted(Formatting.GREEN));
            message.append(Text.literal(" - ").formatted(Formatting.DARK_GRAY));
            message.append(command.getDescription());
            message.append(Text.literal("\n"));
        }
        message.append(Text.literal("---------------------").formatted(Formatting.DARK_GRAY));

        PlayerActionUtil.notifyAll(message.getString());

        return 1;
    }
}