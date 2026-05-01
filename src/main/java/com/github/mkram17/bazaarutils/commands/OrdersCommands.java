package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

@Command
public final class OrdersCommands implements BUCommand {

    @Getter
    public final String commandName = "orders";

    @Getter
    public final Component description = Component.literal("Commands to interact with orders structures & storages.").withStyle(ChatFormatting.GRAY);

    /**
     * Resolves a tracked order by its list index, notifying the player and returning empty when the
     * index is out of range instead of throwing an {@link IndexOutOfBoundsException}.
     */
    public static Optional<Order> orderAt(int index, ProfileKey key) {
        List<Order> orders = UserOrdersStorage.orders(key);

        if (index < 0 || index >= orders.size()) {
            PlayerActionUtil.notifyAll("Invalid order index " + index + " — you have " + orders.size() + " tracked order(s).", NotificationType.COMMAND);

            return Optional.empty();
        }

        return Optional.of(orders.get(index));
    }

    @Override
    public boolean shouldRegister() {
        return DeveloperCommands.isEnabled();
    }

    @Override
    public List<BUCommand> getSubcommands() {
        return BUCommand.childrenOf(OrdersCommands.class);
    }


    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        getSubcommands().forEach(cmd -> cmd.getCommandBuilders().forEach(base::then));

        return base;
    }
}
