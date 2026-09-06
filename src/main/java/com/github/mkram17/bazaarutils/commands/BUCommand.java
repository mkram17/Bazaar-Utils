package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public interface BUCommand {
    String getCommandName();

    /**
     * Collects every registered {@link BUCommand} whose {@link Command#parent()} is {@code parent}.
     * A command with no {@code @Command} annotation is treated as a root command, i.e. it only
     * matches when {@code parent == BUCommand.class}. Commands that opt out via
     * {@link #shouldRegister()} are excluded.
     */
    static List<BUCommand> childrenOf(Class<? extends BUCommand> parent) {
        return BazaarUtilsCommands.collected.stream()
                .filter(it -> it instanceof BUCommand)
                .map(it -> (BUCommand) it)
                .filter(it -> {
                    Command ann = it.getClass().getAnnotation(Command.class);

                    return ann == null ? parent == BUCommand.class : ann.parent() == parent;
                })
                .filter(BUCommand::shouldRegister)
                .toList();
    }

    /**
     * Whether this command should be attached to the dispatcher at all. Commands gated behind a
     * toggle return {@code false} to stay absent from both the command tree and {@code /bu help},
     * along with their whole subtree.
     */
    default boolean shouldRegister() {
        return true;
    }

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

        builders.add(getCommandBuilder(ClientCommands.literal(getCommandName())));

        for (String alias : getAliases()) {
            builders.add(getCommandBuilder(ClientCommands.literal(alias)));
        }

        return builders;
    }
}