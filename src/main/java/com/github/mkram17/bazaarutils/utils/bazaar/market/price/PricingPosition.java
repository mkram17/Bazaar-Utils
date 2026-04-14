package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

public enum PricingPosition {
    COMPETITIVE,
    MATCHED,
    OUTBID;

    public double adjust(double market, TransactionType transaction) {
        boolean higherIsBetter = transaction.higherIsBetter();

        return switch (this) {
            case COMPETITIVE -> higherIsBetter ? market + 0.1 : market - 0.1;
            case MATCHED -> market;
            case OUTBID -> higherIsBetter ? market - 0.1 : market + 0.1;
        };
    }
}