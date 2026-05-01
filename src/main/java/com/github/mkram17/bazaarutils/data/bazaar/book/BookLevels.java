package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;

import java.util.List;

/** Paired ask and bid price levels for one product, as carried by a single API snapshot. */
public record BookLevels<O extends BazaarDataOrigin>(List<PriceLevel<O>> asksLevels, List<PriceLevel<O>> bidsLevels) {
    /** {@code true} when neither side carries any levels. */
    public boolean isEmpty() {
        return asksLevels.isEmpty() && bidsLevels.isEmpty();
    }
}
