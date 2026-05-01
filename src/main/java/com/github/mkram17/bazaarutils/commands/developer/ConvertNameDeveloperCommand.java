package com.github.mkram17.bazaarutils.commands.developer;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.DeveloperCommands;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Command(parent = DeveloperCommands.class)
public final class ConvertNameDeveloperCommand implements BUCommand {
    @Getter
    public final String commandName = "convertname";

    @Getter
    public final Component description = Component.literal("Converts an item name to its product ID.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(ClientCommands.argument("item name", StringArgumentType.string()).executes(this::convertNameToId));
    }

    private int convertNameToId(CommandContext<FabricClientCommandSource> context) {
        if (!DeveloperCommands.isEnabled()) return 0;

        String name = StringArgumentType.getString(context, "item name").replaceAll("_", " ");
        ProductInfo.fromDisplayName(name).ifPresentOrElse(
                id -> PlayerActionUtil.notifyAll(name + ": " + id),
                () -> PlayerActionUtil.notifyAll("Could not find product ID for " + name)
        );

        return 1;
    }
}