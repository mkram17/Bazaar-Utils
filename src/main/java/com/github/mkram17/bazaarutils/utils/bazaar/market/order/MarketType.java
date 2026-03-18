package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

public enum MarketType {
    INSTANT,
    ORDER;

    public PriceType resolvePriceType(TransactionType2 transactionType) {
        return switch (this) {
            case INSTANT -> transactionType.getSide().asPriceType();
            case ORDER -> transactionType.getSide().asPriceType().opposite();
        };
    }
}
