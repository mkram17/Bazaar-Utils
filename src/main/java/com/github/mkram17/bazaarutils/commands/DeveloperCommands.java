package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

@Command
public final class DeveloperCommands implements BUCommand {
    @Getter
    public final String commandName = "developer";

    @Getter
    public final Component description = Component.literal("Toggles developer mode.").withStyle(ChatFormatting.GRAY);

    @Override
    public List<BUCommand> getSubcommands() {
        return BUCommand.childrenOf(DeveloperCommands.class);
    }


    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        getSubcommands().forEach(cmd -> cmd.getCommandBuilders().forEach(base::then));

        return base.executes(this::toggleDeveloperMode);
    }

    private int toggleDeveloperMode(CommandContext<FabricClientCommandSource> context) {
        DeveloperConfig.DEVELOPER_MODE_TOGGLE = !DeveloperConfig.DEVELOPER_MODE_TOGGLE;

        ConfigUtil.scheduleConfigSave();

        PlayerActionUtil.notifyAll(
                DeveloperConfig.DEVELOPER_MODE_TOGGLE
                        ? "Developer mode enabled."
                        : "Developer mode disabled. Restart for all changes to take effect"
        );

        return 1;
    }

    public static boolean isEnabled() {
        return DeveloperConfig.DEVELOPER_MODE_TOGGLE;
    }
}