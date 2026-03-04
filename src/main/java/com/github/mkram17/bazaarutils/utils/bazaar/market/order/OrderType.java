package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import lombok.Getter;

@Getter
public enum OrderType {
    BUY {
        @Override
        public PriceType asPriceType() {
            return PriceType.INSTASELL;
        }
    },

    SELL {
        @Override
        public PriceType asPriceType() {
            return PriceType.INSTABUY;
        }
    };

    public abstract PriceType asPriceType();

    public String getString() {
        return switch (this) {
            case SELL -> "Sell";
            case BUY -> "Buy";
        };
    }

    private OrderType opposite;

    static {
        SELL.opposite = BUY;
        BUY.opposite = SELL;
    }
}

