package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Optional;

@Command(parent = OrdersCommands.class)
public final class RemoveOrderCommand implements BUCommand {
    @Getter
    public final String commandName = "remove";

    @Getter
    public final Component description = Component.literal("Removes an order by index.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(ClientCommands.argument("index", IntegerArgumentType.integer(0)).executes(this::removeByIndex));
    }

    private int removeByIndex(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");

        ProfileKey key = ProfileKey.requireProfile("RemoveOrderCommand"); if (key == null) return 0;

        Optional<Order> order = OrdersCommands.orderAt(index, key);
        if (order.isEmpty()) return 0;

        UserOrdersStorage.apply(key, UserOrdersStorage.StorageOp.cancel(order.get(), new BazaarDataOrigin.OrderCancelled(System.currentTimeMillis()))
                        .then(UserOrdersStorage.StorageOp.reindex()));

        PlayerActionUtil.notifyAll("Removed " + order, NotificationType.COMMAND);

        return 1;
    }
}
