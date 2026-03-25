package com.github.mkram17.bazaarutils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public interface BUCommand {
    String getCommandName();

    default Component getDescription() {
        return Component.empty();
    }

    default List<String> getAliases() {
        return List.of();
    }

    default List<BUCommand> getSubcommands() { return List.of(); }

    LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base);

    default List<LiteralArgumentBuilder<FabricClientCommandSource>> getCommandBuilders() {
        List<LiteralArgumentBuilder<FabricClientCommandSource>> builders = new ArrayList<>();

        builders.add(getCommandBuilder(ClientCommandManager.literal(getCommandName())));

        for (String alias : getAliases()) {
            builders.add(getCommandBuilder(ClientCommandManager.literal(alias)));
        }

        return builders;
    }
}