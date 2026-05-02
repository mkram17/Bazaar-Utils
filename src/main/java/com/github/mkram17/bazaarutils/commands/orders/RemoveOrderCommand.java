package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

@Command(parent = OrdersCommands.class)
public final class RemoveOrderCommand implements BUCommand {
    @Getter
    public final String commandName = "remove";

    @Getter
    public final Component description = Component.literal("Removes an order by index.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::removeByIndex));
    }

    private int removeByIndex(CommandContext<FabricClientCommandSource> context) {
        List<Order> orders = UserOrdersStorage.orders();
        int index = IntegerArgumentType.getInteger(context, "index");

        if (index >= orders.size()) {
            PlayerLogger.send("No order at index %d.".formatted(index));
            return 0;
        }

        Order order = orders.get(index);
        UserOrdersStorage.apply(
                UserOrdersStorage.StorageOp.cancel(order, new BazaarDataOrigin.OrderCancelled(System.currentTimeMillis()))
                        .then(UserOrdersStorage.StorageOp.reindex()));
        PlayerLogger.send("Removed %s".formatted(order.describe()));

        return 1;
    }
}
