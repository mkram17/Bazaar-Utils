package com.github.mkram17.bazaarutils.data.bazaar;

import com.github.mkram17.bazaarutils.data.bazaar.book.LevelReconciliation;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class MarketQuery {
    private MarketQuery() {}

    /**
     * Resolves {@code productId} to its {@link ProductData}, or empty when
     * the ID is not a recognized product or the registry has never seen it.
     */
    private static @NotNull Optional<ProductData> dataFor(@Nullable String productId) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return Optional.empty();
        }

        return Optional.ofNullable(BazaarDataRegistry.get(productId));
    }

    /** The full reconciliation at one price — see {@link ProductData#entryAt}. */
    public static @NotNull Optional<LevelReconciliation> entryAt(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return dataFor(productId).flatMap(data -> data.entryAt(transaction, pricePerUnit));
    }

    /** The prevailing level at one price, kept only when it carries real volume. */
    public static @NotNull Optional<PriceLevel> tradableLevel(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return entryAt(productId, transaction, pricePerUnit).flatMap(LevelReconciliation::tradable);
    }

    /** The top-of-book price on this side, or empty if nothing tradable is stored. */
    public static @NotNull OptionalDouble bestPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        var entry = dataFor(productId).map(data -> data.tradableLevels(transaction).firstEntry()).orElse(null);

        return entry == null ? OptionalDouble.empty() : OptionalDouble.of(entry.getKey());
    }

    /** The open order count at one price, or empty if the level is absent or carries no live volume. */
    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return tradableLevel(productId, transaction, pricePerUnit)
                .map(level -> OptionalInt.of(level.orderCount()))
                .orElse(OptionalInt.empty());
    }

    /** The total volume at one price, or empty if the level is absent or carries no live volume. */
    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return tradableLevel(productId, transaction, pricePerUnit)
                .map(level -> OptionalInt.of((int) level.totalVolume()))
                .orElse(OptionalInt.empty());
    }

    /** How many tradable levels sit strictly ahead of one price, or empty if the product has no data at all. */
    public static @NotNull OptionalInt positionOf(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return dataFor(productId)
                .map(data -> OptionalInt.of(data.positionOf(transaction, pricePerUnit)))
                .orElse(OptionalInt.empty());
    }
}