package com.github.mkram17.bazaarutils.commands;


import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

@Command
public final class RegistryCommands implements BUCommand {

    @Getter
    public final String commandName = "registry";

    @Getter
    public final Component description = Component.literal("Commands to interact with the prices registry.").withStyle(ChatFormatting.GRAY);

    @Override
    public List<BUCommand> getSubcommands() {
        return BazaarUtilsCommands.collected.stream()
                .filter(it -> it instanceof BUCommand)
                .map(it -> (BUCommand) it)
                .filter(it -> {
                    Command ann = it.getClass().getAnnotation(Command.class);

                    return ann != null && ann.parent() == RegistryCommands.class;
                })
                .toList();
    }


    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        getSubcommands().forEach(cmd -> cmd.getCommandBuilders().forEach(base::then));

        return base;
    }
}
