package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.DeveloperCommands;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Command(parent = OrdersCommands.class)
public final class ListOrdersCommand implements BUCommand {
    @Getter
    public final String commandName = "list";

    @Getter
    public final Component description = Component.literal("Lists all watched orders.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(this::queryAll);
    }

    private int queryAll(CommandContext<FabricClientCommandSource> context) {
        if (!DeveloperCommands.isEnabled()) return 0;

        var storage = UserOrdersStorage.orders();
        if (storage.isEmpty()) {
            PlayerLogger.send("No tracked orders.");

            return 1;
        }

        for (int i = 0; i < storage.size(); i++) {
            Order order = storage.get(i);

            PlayerLogger.send("[%d] %s".formatted(i, order.describe()));
        }

        return 1;
    }
}