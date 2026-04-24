package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.Command;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
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
            new ListCommand(),
            new RegistryCommand()
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

    private static final class RegistryCommand implements BUCommand {
        @Getter
        public final String commandName = "registry";

        @Getter
        public final Component description = Component.literal("Prints the in-game book view for a product's order book.").withStyle(ChatFormatting.GRAY);

        private static final int DEFAULT_LEVELS = 3;
        private static final int MAX_LEVELS = 10;

        @Override
        public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
            return base.then(
                    ClientCommandManager.argument("productId", StringArgumentType.word())
                            // /bu developer registry <productId>
                            .executes(context -> printBook(context, TransactionType.Side.BUY, 1, DEFAULT_LEVELS))
                            .then(ClientCommandManager.argument("side", StringArgumentType.word())
                                    .suggests((context, builder) -> { builder.suggest("buy"); builder.suggest("sell"); return builder.buildFuture(); })
                                    // /bu developer registry <productId> buy|sell
                                    .executes(context -> printBook(context, parseSide(context), 1, DEFAULT_LEVELS))
                                    .then(ClientCommandManager.argument("range", StringArgumentType.word())
                                            .suggests((context, builder) -> { builder.suggest("1..5"); builder.suggest("1..10"); builder.suggest("3..7"); return builder.buildFuture(); })
                                            .executes(context -> {
                                                int[] bounds = parseRange(StringArgumentType.getString(context, "range"));

                                                return printBook(context, parseSide(context), bounds[0], bounds[1]);
                                            })
                                    )
                            )
            );
        }

        private static int printBook(CommandContext<FabricClientCommandSource> context, TransactionType.Side side, int from, int to) {
            if (!isEnabled()) return 0;

            String productId = StringArgumentType.getString(context, "productId");
            var data = BazaarDataRegistry.get(productId);

            if (data == null) {
                PlayerActionUtil.notifyAll("No data for: " + productId);

                return 0;
            }

            var book = data.bookFor(side);

            PlayerActionUtil.notifyAll(Component.literal("Top " + side + " Orders:").withStyle(ChatFormatting.GREEN));

            if (book.isEmpty()) {
                PlayerActionUtil.notifyAll(Component.literal("(empty)").withStyle(ChatFormatting.GRAY));

                return 1;
            }

            book.entrySet().stream()
                    .skip(from - 1)
                    .limit(to - from + 1)
                    .forEach(entry -> PlayerActionUtil.notifyAll(formatLevel(entry.getKey(), entry.getValue())));

            return 1;
        }

        /** Mirrors the NBT lore coloring exactly, with source appended in dark_gray. */
        private static Component formatLevel(double price, PriceLevel level) {
            int orders = level.orderCount();
            String volume = String.format("%,d", level.totalVolume());
            String quantifier = orders == 1 ? "order" : "orders";

            return Component.literal(formatPrice(price) + " coins ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("each | ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(volume).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("x ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("in ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(orders + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(quantifier).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" [" + level.source().describe() + "]").withStyle(ChatFormatting.DARK_GRAY));
        }


        private static String formatPrice(double price) {
            if (price == Math.floor(price)) return String.format("%,.0f", price);

            long whole = (long) price;

            return String.format("%,d", whole) + String.format("%.1f", price - whole).substring(1);
        }

        /**
         * Parses a range string into a [from, to] pair (both 1-based inclusive).
         * Accepts:
         *   "3..10"  → [3, 10]
         *   "..5"    → [1, 5]
         *   "3.."    → [3, MAX_LEVELS]
         *   "5"      → [5, 5]
         */
        private static int[] parseRange(String raw) {
            if (raw.contains("..")) {
                String[] parts = raw.split("\\.\\.", -1);

                int from = parts[0].isBlank() ? 1 : Math.max(1, Integer.parseInt(parts[0]));
                int to = parts[1].isBlank() ? MAX_LEVELS : Math.min(MAX_LEVELS, Integer.parseInt(parts[1]));

                if (from > to) {
                    int tmp = from; from = to; to = tmp; // swap if inverted
                }

                return new int[]{from, to};
            }

            int n = Math.clamp(Integer.parseInt(raw), 1, MAX_LEVELS);

            return new int[]{n, n};
        }

        private static TransactionType.Side parseSide(CommandContext<FabricClientCommandSource> ctx) {
            return switch (StringArgumentType.getString(ctx, "side").toLowerCase()) {
                case "sell", "s" -> TransactionType.Side.SELL;
                default          -> TransactionType.Side.BUY;
            };
        }
    }
}