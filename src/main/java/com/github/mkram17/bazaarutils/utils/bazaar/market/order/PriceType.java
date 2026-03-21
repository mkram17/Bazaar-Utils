package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

/**
 * Bazaar API price buckets used to quote the current market side.
 */
public enum PriceType {
    INSTABUY,
    INSTASELL;

    /**
     * Returns the opposite market side.
     */
    public PriceType opposite(){
        return this == INSTABUY ? INSTASELL : INSTABUY;
    }

    /**
     * Checks whether this resolves to a {@link TransactionType}.
     */
    public boolean is(TransactionType transactionType) {
        return this == transactionType.getPriceType();
    }
}