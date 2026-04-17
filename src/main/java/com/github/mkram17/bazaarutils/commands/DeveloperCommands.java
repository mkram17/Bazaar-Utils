package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.Command;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Command
public final class DeveloperCommands implements BUCommand {
    @Getter
    public final String commandName = "developer";

    @Getter
    public final Component description = Component.literal("Toggles developer mode.").withStyle(ChatFormatting.GRAY);

    @Getter
    private final List<BUCommand> subcommands = List.of(
            new RemoveCommand(),
            new InfoCommand(),
            new OutdatedCommand(),
            new ConvertNameCommand(),
            new ListCommand()
    );

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        subcommands.forEach(cmd -> cmd.getCommandBuilders().forEach(base::then));

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

    private static boolean isEnabled() {
        return DeveloperConfig.DEVELOPER_MODE_TOGGLE;
    }

    private static final class RemoveCommand implements BUCommand {
        @Getter
        public final String commandName = "remove";

        @Getter
        public final Component description = Component.literal("Removes an order by index.").withStyle(ChatFormatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::removeByIndex));
        }

        private int removeByIndex(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            List<Order> orders = UserOrdersStorage.INSTANCE.get();
            int index = IntegerArgumentType.getInteger(context, "index");

            if (orders == null || index >= orders.size()) {
                PlayerActionUtil.notifyAll("No order at index " + index + ".", NotificationType.COMMAND);
                return 0;
            }

            Order order = orders.get(index);
            UserOrdersStorage.cancelAndReindex(order);
            PlayerActionUtil.notifyAll("Removed " + order, NotificationType.COMMAND);
            return 1;
        }
    }

    private static final class InfoCommand implements BUCommand {
        @Getter
        public final String commandName = "info";

        @Getter
        public final Component description = Component.literal("Prints info about an order by index.").withStyle(ChatFormatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::queryByIndex));
        }

        private int queryByIndex(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            List<Order> orders = UserOrdersStorage.INSTANCE.get();
            int index = IntegerArgumentType.getInteger(context, "index");

            if (orders == null || index >= orders.size()) {
                PlayerActionUtil.notifyAll("No order at index " + index + ".");
                return 0;
            }

            PlayerActionUtil.notifyAll(orders.get(index).toString());
            return 1;
        }
    }

    private static final class OutdatedCommand implements BUCommand {
        @Getter
        public final String commandName = "outdated";

        @Getter
        public final Component description = Component.literal("Lists all outdated orders.").withStyle(ChatFormatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.executes(this::queryOutdated);
        }

        private int queryOutdated(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            List<Order> orders = UserOrdersStorage.INSTANCE.get();
            if (orders == null || orders.isEmpty()) {
                PlayerActionUtil.notifyAll("No orders loaded.");

                return 1;
            }

            for (Order order : orders) {
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

    private static final class ConvertNameCommand implements BUCommand {
        @Getter
        public final String commandName = "convertname";

        @Getter
        public final Component description = Component.literal("Converts an item name to its product ID.").withStyle(ChatFormatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(ClientCommandManager.argument("item name", StringArgumentType.string()).executes(this::convertNameToId));
        }

        private int convertNameToId(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            String name = StringArgumentType.getString(context, "item name").replace("_", " ");

            ProductInfo.fromDisplayName(name)
                    .map(ProductInfo::getProductId)
                    .ifPresentOrElse(
                            id  -> PlayerActionUtil.notifyAll(name + " → " + id),
                            ()  -> PlayerActionUtil.notifyAll("Could not find product ID for: " + name)
                    );

            return 1;
        }
    }

    private static final class ListCommand implements BUCommand {
        @Getter
        public final String commandName = "list";

        @Getter
        public final Component description = Component.literal("Lists all watched orders.").withStyle(ChatFormatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.executes(this::queryAll);
        }

        private int queryAll(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            List<Order> orders = UserOrdersStorage.INSTANCE.get();
            if (orders == null || orders.isEmpty()) {
                PlayerActionUtil.notifyAll("No tracked orders.");
                return 1;
            }

            for (int i = 0; i < orders.size(); i++) {
                Order order = orders.get(i);
                String name = ResourceManager.getProductIdtoNameCache().getOrDefault(order.productId(), order.productId());
                PlayerActionUtil.notifyAll("[" + i + "] " + name + " | " + order.side() + " | " + order.pricePerItem() + " x" + order.originalAmount() + " | " + order.status());
            }

            return 1;
        }
    }
}