package com.github.mkram17.bazaarutils.features.restrictsell;

import lombok.Getter;
import lombok.Setter;

public class RestrictSellControl {
    @Getter
    @Setter
    private boolean enabled = true;
    @Getter @Setter
    private RestrictSell.restrictBy restriction;
    @Getter @Setter
    private double amount;

    public RestrictSellControl(RestrictSell.restrictBy restriction, double amount) {
        this.restriction = restriction;
        this.amount = amount;
    }
}
