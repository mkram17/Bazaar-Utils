package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import lombok.Getter;

@Getter
public enum PriceType {
    INSTABUY,
    INSTASELL;

    public String getString() {
        return switch (this) {
            case INSTASELL -> "Buy";
            case INSTABUY -> "Sell";
        };
    }

    public PriceType opposite;

    static {
        INSTASELL.opposite = INSTABUY;
        INSTABUY.opposite = INSTASELL;
    }
}
