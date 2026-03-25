package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.features.notification.OutbidOrderHandler;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

@Module
public final class DeveloperCommands implements BUCommand {
    @Getter
    public final String commandName = "developer";

    @Getter
    public final Text description = Text.literal("Toggles developer mode.").formatted(Formatting.GRAY);

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
        public final Text description = Text.literal("Removes an order by index.").formatted(Formatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::removeByIndex));
        }

        private int removeByIndex(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            int index = IntegerArgumentType.getInteger(context, "index");

            Order order = UserOrdersStorage.INSTANCE.get().get(index);
            order.removeFromUserOrders();
            PlayerActionUtil.notifyAll("Removed " + order, NotificationType.COMMAND);

            return 1;
        }
    }

    private static final class InfoCommand implements BUCommand {
        @Getter
        public final String commandName = "info";

        @Getter
        public final Text description = Text.literal("Prints info about an order by index.").formatted(Formatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(ClientCommandManager.argument("index", IntegerArgumentType.integer()).executes(this::queryByIndex));
        }

        private int queryByIndex(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            int index = IntegerArgumentType.getInteger(context, "index");
            PlayerActionUtil.notifyAll(UserOrdersStorage.INSTANCE.get().get(index).toString());

            return 1;
        }
    }

    private static final class OutdatedCommand implements BUCommand {
        @Getter
        public final String commandName = "outdated";

        @Getter
        public final Text description = Text.literal("Lists all outdated orders.").formatted(Formatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.executes(this::queryOutdated);
        }

        private int queryOutdated(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            for (Order item : OutbidOrderHandler.getOutbidOrders()) {
                PlayerActionUtil.notifyAll(item.getName() + " is outdated. Market Price: "
                        + item.getMarketPrice(TransactionType.Side.BUY) + " Order Price: " + item.getPricePerItem());
            }

            return 1;
        }
    }

    private static final class ConvertNameCommand implements BUCommand {
        @Getter
        public final String commandName = "convertname";

        @Getter
        public final Text description = Text.literal("Converts an item name to its product ID.").formatted(Formatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(
                LiteralArgumentBuilder<FabricClientCommandSource> base
        ) {
            return base.then(ClientCommandManager.argument("item name", StringArgumentType.string()).executes(this::convertNameToId));
        }

        private int convertNameToId(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            String name = StringArgumentType.getString(context, "item name").replaceAll("_", " ");
            BazaarDataUtil.findProductIdOptional(name).ifPresentOrElse(
                    id -> PlayerActionUtil.notifyAll(name + ": " + id),
                    () -> PlayerActionUtil.notifyAll("Could not find product ID for " + name)
            );

            return 1;
        }
    }

    private static final class ListCommand implements BUCommand {
        @Getter
        public final String commandName = "list";

        @Getter
        public final Text description = Text.literal("Lists all watched orders.").formatted(Formatting.GRAY);

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.executes(this::queryAll);
        }

        private int queryAll(CommandContext<FabricClientCommandSource> context) {
            if (!isEnabled()) return 0;

            PlayerActionUtil.notifyAll(Order.getVariables(Order::getName).toString());

            return 1;
        }
    }
}