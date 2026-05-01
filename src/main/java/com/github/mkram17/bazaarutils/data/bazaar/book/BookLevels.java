package com.github.mkram17.bazaarutils.data.bazaar.book;

import java.util.List;

/** Paired ask and bid price levels for one product, as carried by a single API snapshot. */
public record BookLevels(List<PriceLevel> asksLevels, List<PriceLevel> bidsLevels) {
    /** {@code true} when neither side carries any levels. */
    public boolean isEmpty() {
        return asksLevels.isEmpty() && bidsLevels.isEmpty();
    }
}
