package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

public enum MarketType {
    INSTANT,
    ORDER;

    public PriceType resolvePriceType(TransactionType transactionType) {
        return switch (this) {
            case INSTANT -> transactionType.asPriceType();
            case ORDER -> transactionType.opposite().asPriceType();
        };
    }
}
