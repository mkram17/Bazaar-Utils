package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Command(parent = OrdersCommands.class)
public final class QueryOutdatedCommand implements BUCommand {
    @Getter
    public final String commandName = "outdated";

    @Getter
    public final Component description = Component.literal("Lists all outdated orders.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(this::queryOutdated);
    }

    private int queryOutdated(CommandContext<FabricClientCommandSource> context) {
        for (Order item : OutbidOrderHandler.getOutbidOrders()) {
            PlayerActionUtil.notifyAll(item.getName() + " is outdated. Market Price: "
                    + item.getMarketPrice(TransactionType.Side.BUY) + " Order Price: " + item.getPricePerItem());
        }

        return 1;
    }
}