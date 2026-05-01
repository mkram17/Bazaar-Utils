package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

public enum PricingPosition {
    COMPETITIVE,
    MATCHED,
    OUTBID;

    public double adjust(double market, TransactionType transaction) {
        boolean higherIsBetter = transaction.higherIsBetter();

        double raw = switch (this) {
            case COMPETITIVE -> higherIsBetter ? market + 0.1 : market - 0.1;
            case MATCHED -> market;
            case OUTBID -> higherIsBetter ? market - 0.1 : market + 0.1;
        };

        if (higherIsBetter) {
            // Raise to the stricter of the absolute floor and Hypixel's 2/3 bid floor.
            return Math.max(Math.max(PriceInfo.MINIMUM_PRICE, PriceInfo.minimumBid(market)), raw);
        } else {
            // Clamp between the absolute floor and Hypixel's 3/2 ask ceiling.
            return Math.clamp(raw, PriceInfo.MINIMUM_PRICE, PriceInfo.maximumAsk(market));
        }
    }
}