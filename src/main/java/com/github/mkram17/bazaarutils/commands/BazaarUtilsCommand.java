package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;

@LateInitModule
public final class BazaarUtilsCommand implements BUCommand {
    private static final List<String> PREFIXES = List.of("bazaarutils", "bu");

    @Getter
    public final String commandName = "bazaarutils";

    @Getter
    private final List<BUCommand> subcommands;

    public BazaarUtilsCommand() {
        this.subcommands = BazaarUtilsCommands.collected.stream()
                .filter(it -> it instanceof BUCommand)
                .map(it -> (BUCommand) it)
                .toList();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerWithCommands(dispatcher, subcommands)
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