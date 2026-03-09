package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

public enum TransactionType {
    BUY, SELL;

    public TransactionType opposite() {
        return this == BUY ? SELL : BUY;
    }

    public PriceType asPriceType() {
        return this == BUY ? PriceType.INSTABUY : PriceType.INSTASELL;
    }
}