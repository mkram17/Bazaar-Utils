package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;

@LateInitModule
public final class BazaarUtilsCommands implements BUCommand {
    @Getter
    public final String commandName = "bazaarutils";

    private static final List<String> PREFIXES = List.of("bazaarutils", "bu");

    public BazaarUtilsCommands() {
        List<BUCommand> subcommands = BazaarUtilsModules.collected.stream()
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