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
 * <p>Single-price lookups ({@link #entryAt}) delegate straight to
 * {@link ProductData#entryAt} — there's no benefit to a push-based query over exactly
 * one value. {@link #side} is the one place {@link LevelQuery} actually earns its
 * keep: pushing every level on a side, in book order, until the query itself signals
 * it's done, and pushing nothing at all when the product is unknown — so every
 * {@link LevelQuery} used here has to produce a sensible answer from zero pushes too.
 */
public final class BazaarDataQuery {
    private BazaarDataQuery() {}

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

    /** Extracts the pushed level's prevailing {@link PriceLevel}, kept only when it carries real volume. */
    static LevelQuery<Optional<PriceLevel<?>>> tradableLevel() {
        return LevelQuery.first(LevelReconciliation::tradable);
    }

    static LevelQuery<OptionalDouble> bestPrice() {
        return tradableLevel().andThen(level -> level.map(PriceLevel::pricePerUnit).map(OptionalDouble::of).orElse(OptionalDouble.empty()));
    }

    /**
     * Counts tradable levels reached before {@code price} in book order, stopping the
     * instant a level matching {@code price} is reached — that level itself is never
     * counted, tradable or not. If {@code price} is never reached at all, this counts
     * every tradable level on the side instead of stopping early; the public overload
     * below only guards the "product doesn't exist" case separately from this one.
     */
    static LevelQuery<Long> positionOf(double price) {
        return LevelQuery.takingWhile(
                level -> Double.compare(level.pricePerUnit(), price) != 0,
                LevelQuery.filtering(level -> level.tradable().isPresent(), LevelQuery.counting())
        );
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
        return side(productId, type, bestPrice());
    }

    public static @NotNull OptionalDouble bestPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        return side(productId, transaction.getPriceType(), bestPrice());
    }

    /** The open order count at one price, or empty if the level is absent or carries no live volume. */
    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return tradableLevel(productId, type, pricePerUnit).map(PriceLevel::orderCount).map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return orderCount(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** The total volume at one price, or empty if the level is absent or carries no live volume. */
    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        return tradableLevel(productId, type, pricePerUnit).map(level -> (int) level.totalVolume()).map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return totalVolume(productId, transaction.getPriceType(), pricePerUnit);
    }

    /** How many tradable levels sit strictly ahead of one price, or empty if the product has no data at all. */
    public static @NotNull OptionalInt positionOf(@Nullable String productId, @NotNull PriceType type, double pricePerUnit) {
        if (dataFor(productId).isEmpty()) return OptionalInt.empty();

        return OptionalInt.of(Math.toIntExact(side(productId, type, positionOf(pricePerUnit))));
    }

    public static @NotNull OptionalInt positionOf(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return positionOf(productId, transaction.getPriceType(), pricePerUnit);
    }
}