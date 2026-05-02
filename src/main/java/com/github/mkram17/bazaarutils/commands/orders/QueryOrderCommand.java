package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Command(parent = OrdersCommands.class)
public final class QueryOrderCommand implements BUCommand {
    @Getter
    public final String commandName = "info";

    @Getter
    public final Component description = Component.literal("Prints info about an order by index.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::queryByIndex));
    }

    private int queryByIndex(CommandContext<FabricClientCommandSource> context) {
        var storage = UserOrdersStorage.orders();
        int index = IntegerArgumentType.getInteger(context, "index");

        if (index >= storage.size()) {
            PlayerLogger.send("No order at index " + index + ".");

            return 0;
        }

        PlayerLogger.send(storage.get(index).toString());

        return 1;
    }
}