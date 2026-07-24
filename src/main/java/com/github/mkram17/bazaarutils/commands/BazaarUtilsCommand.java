package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;

@Module
public final class BazaarUtilsCommand implements BUCommand {
    @Getter
    public final String commandName = "bazaarutils";

    public List<BUCommand> getSubcommands() {
        return BazaarUtilsCommands.collected.stream()
                .filter(it -> it instanceof BUCommand)
                .map(it -> (BUCommand) it)
                .filter(it -> {
                    Command ann = it.getClass().getAnnotation(Command.class);

                    return ann == null || ann.parent() == BUCommand.class;
                })
                .toList();
    }

    private static final List<String> PREFIXES = List.of("bazaarutils", "bu");

    public BazaarUtilsCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerWithCommands(dispatcher, getSubcommands())
        );
    }

    private void registerWithCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, List<BUCommand> subcommands) {
        for (String prefix : PREFIXES) {
            LiteralArgumentBuilder<FabricClientCommandSource> base = getCommandBuilder(ClientCommandManager.literal(prefix));

            subcommands.forEach(command -> command.getCommandBuilders().forEach(base::then));

            dispatcher.register(base);
        }
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            ConfigUtil.openGUI();

            return 1;
        });
    }
}