package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.mojang.serialization.Codec;

/**
 * Bazaar API price buckets used to quote the current market side.
 */
public enum PriceType {
    INSTABUY,
    INSTASELL;

    /**
     * Returns the opposite market side.
     */
    public PriceType opposite() {
        return this == INSTABUY ? INSTASELL : INSTABUY;
    }

    public boolean higherIsBetter() {
        return this == INSTASELL;
    }

    /** {@code true} when {@code price} is at least as favorable as {@code reference} in this book's direction. Ties count. */
    public boolean atLeastAsGood(double price, double reference) {
        return higherIsBetter() ? price >= reference : price <= reference;
    }

    /** {@code true} when {@code price} is strictly more favorable than {@code reference}. Ties do not count. */
    public boolean strictlyBetter(double price, double reference) {
        return higherIsBetter() ? price > reference : price < reference;
    }

    /**
     * Checks whether this resolves to a {@link TransactionType}.
     */
    public boolean is(TransactionType transactionType) {
        return this == transactionType.getPriceType();
    }

    public static final Codec<PriceType> CODEC = Codec.STRING.xmap(PriceType::valueOf, Enum::name);
}