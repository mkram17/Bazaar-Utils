package com.github.mkram17.bazaarutils.commands.registry;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.RegistryCommands;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.resources.BazaarConversions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

@Command(parent = RegistryCommands.class)
public final class QueryRegistryCommand implements BUCommand {
    @Getter
    public final String commandName = "query";

    @Getter
    public final Component description = Component.literal("Prints a product's order book, base/overlay lineage included.").withStyle(ChatFormatting.GRAY);

    private static final int DEFAULT_LEVELS = 3;
    private static final int MAX_LEVELS = 100;

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(
                ClientCommands.argument("productId", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toUpperCase(Locale.ROOT);

                            BazaarConversions.getProductIdToNameCache().forEach((id, name) -> {
                                if (id.startsWith(input)) builder.suggest(id, () -> name);
                            });

                            return builder.buildFuture();
                        })
                        .executes(context -> printBook(context, PriceType.INSTABUY, 1, DEFAULT_LEVELS))
                        .then(ClientCommands.argument("pricetype", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("instabuy");
                                    builder.suggest("instasell");

                                    return builder.buildFuture();
                                })
                                .executes(context -> printBook(context, parsePriceType(context), 1, DEFAULT_LEVELS))
                                .then(ClientCommands.argument("range", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("1..5");
                                            builder.suggest("1..10");
                                            builder.suggest("3..7");

                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            int[] bounds = parseRange(StringArgumentType.getString(context, "range"));

                                            return printBook(context, parsePriceType(context), bounds[0], bounds[1]);
                                        })
                                )
                        )
        );
    }

    private static int printBook(CommandContext<FabricClientCommandSource> context, PriceType type, int from, int to) {
        String productId = StringArgumentType.getString(context, "productId");
        var data = BazaarDataRegistry.get(productId);

        if (data == null) {
            PlayerActionUtil.notifyAll("No data for: " + productId);

            return 0;
        }

        var book = data.tradableLevels(type);

        PlayerActionUtil.notifyAll(Component.literal(type + " Book:").withStyle(ChatFormatting.GREEN));

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
                .append(Component.literal(" [" + level.origin().describe() + "]").withStyle(ChatFormatting.DARK_GRAY));
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

    private static PriceType parsePriceType(CommandContext<FabricClientCommandSource> ctx) {
        return switch (StringArgumentType.getString(ctx, "pricetype").toLowerCase()) {
            case "instabuy" -> PriceType.INSTABUY;
            default         -> PriceType.INSTASELL;
        };
    }
}