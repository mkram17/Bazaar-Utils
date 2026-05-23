package com.github.mkram17.bazaarutils.data.bazaar;

import com.github.mkram17.bazaarutils.data.bazaar.book.LevelQuery;
import com.github.mkram17.bazaarutils.data.bazaar.book.LevelReconciliation;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * String-keyed façade over the book registry, for callers that hold only a bare
 * {@code productId} rather than a live {@link ProductData} reference.
 *
 * <p>Single-price lookups ({@link #entryAt}, {@link #bestPrice}, {@link #positionOf},
 * {@link #spread}) delegate directly to {@link ProductData}'s cached sorted maps.
 * {@link #side} drives push-based {@link LevelQuery} passes over every reconciled level
 * on a side in book order, pushing nothing when the product is unknown — so every
 * {@link LevelQuery} used here produces a sensible answer from zero pushes too.
 */
public final class BazaarDataQuery {
    private BazaarDataQuery() {}

    /**
     * Two-sided top-of-book snapshot for one product — useful for spread,
     * margin, and crossed/inverted-book checks.
     */
    public record MarketSpread(double bestAsk, double bestBid) {
        /** {@code bestAsk - bestBid} in coins; negative when the book is inverted. */
        public double absoluteSpread() {
            return bestAsk - bestBid;
        }

        /** {@code (bestAsk - bestBid) / bestBid}, or {@code 0.0} when {@code bestBid <= 0}. */
        public double relativeSpread() {
            return bestBid > 0.0 ? (bestAsk - bestBid) / bestBid : 0.0;
        }

        /** Midpoint between best bid and best ask. */
        public double midPrice() {
            return (bestAsk + bestBid) / 2.0;
        }

        /** {@code true} when {@code bestBid >= bestAsk} (crossed or inverted book). */
        public boolean isInverted() {
            return bestBid >= bestAsk;
        }
    }

    /** Resolves {@code productId} to its {@link ProductData}, or empty when the ID is not a recognized product or the registry has never seen it. */
    private static @NotNull Optional<ProductData> dataFor(@Nullable String productId) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return Optional.empty();
        }

        return Optional.ofNullable(BazaarDataRegistry.get(productId));
    }

    /**
     * Pushes every level on {@code type}'s side, in book order, stopping the moment
     * {@code query} signals it's done. Pushes nothing at all when the product is unknown.
     */
    public static <R> R side(@Nullable String productId, @NotNull PriceType type, @NotNull LevelQuery<R> query) {
        dataFor(productId).ifPresent(data -> {
            for (LevelReconciliation level : data.book(type).values()) {
                if (!query.push(level)) break;
            }
        });

        return query.result();
    }

    /** @see #side(String, PriceType, LevelQuery) */
    public static <R> R side(@Nullable String productId, @NotNull TransactionType transaction, @NotNull LevelQuery<R> query) {
        return side(productId, transaction.getPriceType(), query);
    }

    /** The full reconciliation at one price, if either layer of the book has one. */
    public static @NotNull Optional<LevelReconciliation> entryAt(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return dataFor(productId).flatMap(data -> data.entryAt(type, pricePerUnit));
    }

    /** @see #entryAt(String, PriceType, double) */
    public static @NotNull Optional<LevelReconciliation> entryAt(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return entryAt(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** The prevailing level at one price, kept only when it carries real volume. */
    public static @NotNull Optional<PriceLevel<?>> tradableLevel(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return entryAt(productId, type, pricePerUnit).flatMap(LevelReconciliation::tradable);
    }

    public static @NotNull Optional<PriceLevel<?>> tradableLevel(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return tradableLevel(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** The top-of-book price on this side, or empty if nothing tradable is stored. */
    public static @NotNull OptionalDouble bestPrice(@Nullable String productId, @NotNull PriceType type) {
        var dataOpt = dataFor(productId);
        if (dataOpt.isEmpty()) return OptionalDouble.empty();

        var best = dataOpt.get().tradableLevels(type).firstEntry();

        return best != null ? OptionalDouble.of(best.getKey()) : OptionalDouble.empty();
    }

    public static @NotNull OptionalDouble bestPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        return bestPrice(productId, transaction.getPriceType());
    }

    /** Top-of-book ask/bid spread for {@code productId}, or empty if either side lacks tradable liquidity. */
    public static @NotNull Optional<MarketSpread> spread(@Nullable String productId) {
        var dataOpt = dataFor(productId);
        if (dataOpt.isEmpty()) return Optional.empty();

        var data = dataOpt.get();
        var bestAsk = data.tradableLevels(PriceType.INSTABUY).firstEntry();
        var bestBid = data.tradableLevels(PriceType.INSTASELL).firstEntry();
        if (bestAsk == null || bestBid == null) return Optional.empty();

        return Optional.of(new MarketSpread(bestAsk.getKey(), bestBid.getKey()));
    }

    /** The open order count at one price, or empty if the level is absent or carries no live volume. */
    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return tradableLevel(productId, type, pricePerUnit).map(PriceLevel::orderCount).map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return orderCount(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** The total volume at one price (clamped to {@link Integer#MAX_VALUE}), or empty if absent or empty. */
    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return tradableLevel(productId, type, pricePerUnit)
                .map(level -> (int) Math.clamp(level.totalVolume(), 0L, Integer.MAX_VALUE))
                .map(OptionalInt::of)
                .orElse(OptionalInt.empty());
    }

    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return totalVolume(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** How many tradable levels sit strictly ahead of {@code pricePerUnit}, or empty if the product has no data at all. */
    public static @NotNull OptionalInt positionOf(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        var dataOpt = dataFor(productId);
        if (dataOpt.isEmpty()) return OptionalInt.empty();

        return OptionalInt.of(dataOpt.get().positionOf(type, pricePerUnit));
    }

    public static @NotNull OptionalInt positionOf(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return positionOf(productId, transaction.getPriceType(), pricePerUnit);
    }
}
