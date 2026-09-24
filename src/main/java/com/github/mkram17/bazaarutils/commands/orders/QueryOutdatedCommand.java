package com.github.mkram17.bazaarutils.commands.orders;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.OrdersCommands;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

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
        ProfileKey key = ProfileKey.requireProfile("QueryOutdatedCommand"); if (key == null) return 0;
        var storage = UserOrdersStorage.orders(key);

        if (storage.isEmpty()) {
            PlayerActionUtil.notifyAll("No orders loaded for the active profile.");

            return 1;
        }

        for (Order order : storage) {
            if (!(order.status() instanceof OrderStatus.Set || order.status() instanceof OrderStatus.Partial)) continue;

            Optional<PricingPosition> position = PriceInfo.position(order.productId(), TransactionType.of(order.side(), TransactionType.Method.ORDER), order.pricePerItem());
            if (position.isEmpty()) continue;

            OptionalDouble price = PriceInfo.marketPrice(order.productId(), TransactionType.of(order.side(), TransactionType.Method.ORDER));
            if (price.isEmpty()) continue;

            PlayerActionUtil.notifyAll("Outbidded: " + order.describe()
                    + " | Market Price: %.1f".formatted(price.getAsDouble()) );
        }

        return 1;
    }
}