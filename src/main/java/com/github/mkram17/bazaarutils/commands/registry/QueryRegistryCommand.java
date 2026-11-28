package com.github.mkram17.bazaarutils.commands.registry;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.commands.RegistryCommands;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataQuery;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.LevelQuery;
import com.github.mkram17.bazaarutils.data.bazaar.book.LevelReconciliation;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.conversions.BazaarConversions;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
                ClientCommands.argument("productId", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            String raw = builder.getRemaining();
                            String input = (raw.startsWith("\"") ? raw.substring(1) : raw).toUpperCase(Locale.ROOT);

                            BazaarConversions.getProductIdToNameCache().forEach((id, name) -> {
                                if (id.startsWith(input)) {
                                    builder.suggest(StringArgumentType.escapeIfRequired(id), () -> name);
                                }
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
                                .executes(context -> {
                                    PriceType type = parsePriceType(context);
                                    if (type == null) return 0;

                                    return printBook(context, type, 1, DEFAULT_LEVELS);
                                })
                                .then(ClientCommands.argument("range", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("1..5");
                                            builder.suggest("1..10");
                                            builder.suggest("3..7");

                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            PriceType type = parsePriceType(context);
                                            if (type == null) return 0;

                                            String rawRange = StringArgumentType.getString(context, "range");
                                            Optional<int[]> boundsOpt = parseRange(rawRange);
                                            if (boundsOpt.isEmpty()) {
                                                PlayerActionUtil.notifyAll("Invalid range: '" + rawRange + "' (expected e.g. 1..5, ..10, 3.., or 5)");
                                                return 0;
                                            }

                                            int[] bounds = boundsOpt.get();
                                            return printBook(context, type, bounds[0], bounds[1]);
                                        })
                                )
                        )
        );
    }

    private static int printBook(CommandContext<FabricClientCommandSource> context, PriceType type, int from, int to) {
        String productId = StringArgumentType.getString(context, "productId").toUpperCase(Locale.ROOT);

        if (BazaarDataRegistry.get(productId) == null) {
            PlayerActionUtil.notifyAll("No data for: " + productId);

            return 0;
        }

        List<LevelReconciliation> levels = BazaarDataQuery.side(productId, type, LevelQuery.range(from, to));

        PlayerActionUtil.notifyAll(Component.literal(type + " Book:").withStyle(ChatFormatting.GREEN));

        if (levels.isEmpty()) {
            PlayerActionUtil.notifyAll(Component.literal("(empty)").withStyle(ChatFormatting.GRAY));

            return 1;
        }

        int position = from;
        for (LevelReconciliation level : levels) {
            PlayerActionUtil.notifyAll(formatLevel(position++, level));
        }

        return 1;
    }

    private static Component formatLevel(int position, LevelReconciliation reconciliation) {
        double price = reconciliation.pricePerUnit();
        PriceLevel<?> prevailing = reconciliation.prevailing();
        LevelReconciliation.Layer layer = reconciliation.prevailingLayer(prevailing);

        int orders = prevailing.orderCount();
        String volume = String.format("%,d", prevailing.totalVolume());
        String quantifier = orders == 1 ? "order" : "orders";

        var line = Component.literal("#" + position + " ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(formatPrice(price) + " coins ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("each | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(volume).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("x ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("in ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(orders + " ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(quantifier + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[" + layer + ": " + prevailing.origin().describe() + "]").withStyle(ChatFormatting.DARK_GRAY));

        if (reconciliation.isVacated(prevailing)) {
            return line.append(Component.literal(" — vacated by player").withStyle(ChatFormatting.RED));
        }

        if (reconciliation.layersDisagree()) {
            PriceLevel<?> other = layer == LevelReconciliation.Layer.BASE ? reconciliation.overlay() : reconciliation.base();
            if (other != null) {
                String otherVolume = String.format("%,d", other.totalVolume());
                return line.append(Component.literal(" (other: " + otherVolume + "x [" + other.origin().describe() + "])").withStyle(ChatFormatting.YELLOW));
            }
        }

        return line;
    }

    private static String formatPrice(double price) {
        double rounded = Math.round(price * 10.0) / 10.0;
        if (rounded == Math.floor(rounded)) {
            return String.format(Locale.US, "%,.0f", rounded);
        }

        return String.format(Locale.US, "%,.1f", rounded);
    }

    /**
     * Parses a range string into a clamped [from, to] pair in [1, MAX_LEVELS].
     * Accepts: "3..10", "..5", "3..", "5". Returns empty on malformed input.
     */
    private static Optional<int[]> parseRange(String raw) {
        try {
            if (raw.contains("..")) {
                String[] parts = raw.split("\\.\\.", -1);
                if (parts.length != 2) return Optional.empty();

                int from = parts[0].isBlank() ? 1 : Math.clamp(Integer.parseInt(parts[0]), 1, MAX_LEVELS);
                int to = parts[1].isBlank() ? MAX_LEVELS : Math.clamp(Integer.parseInt(parts[1]), 1, MAX_LEVELS);

                if (from > to) {
                    int tmp = from;
                    from = to;
                    to = tmp;
                }

                return Optional.of(new int[]{from, to});
            }

            int n = Math.clamp(Integer.parseInt(raw), 1, MAX_LEVELS);
            return Optional.of(new int[]{n, n});
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static @Nullable PriceType parsePriceType(CommandContext<FabricClientCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "pricetype");
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "instabuy" -> PriceType.INSTABUY;
            case "instasell" -> PriceType.INSTASELL;
            default -> {
                PlayerActionUtil.notifyAll("Invalid price type: '" + raw + "' (expected instabuy or instasell)");
                yield null;
            }
        };
    }
}
