package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** Paired ask and bid price levels for one product, as carried by a single snapshot. */
public record BookLevels<O extends BazaarDataOrigin>(
        @NotNull List<PriceLevel<O>> askLevels,
        @NotNull List<PriceLevel<O>> bidLevels
) {
    public BookLevels {
        askLevels = List.copyOf(Objects.requireNonNull(askLevels, "askLevels"));
        bidLevels = List.copyOf(Objects.requireNonNull(bidLevels, "bidLevels"));
    }

    /** Returns the levels resting on {@code type}'s side of the book. */
    public @NotNull List<PriceLevel<O>> levelsFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? askLevels : bidLevels;
    }
}
