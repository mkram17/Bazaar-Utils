package com.github.mkram17.bazaarutils.features.restrictsell;

import lombok.Getter;
import lombok.Setter;

public class RestrictSellControl {
    @Getter
    @Setter
    private boolean enabled = true;
    @Getter @Setter
    private RestrictSell.restrictBy rule;
    @Getter @Setter
    private double amount;
    @Getter @Setter
    private String name;

    public RestrictSellControl(RestrictSell.restrictBy rule, double amount) {
        this.rule = rule;
        this.amount = amount;
    }
    public RestrictSellControl(RestrictSell.restrictBy rule, String name) {
        this.rule = rule;
        this.name = name;
    }
}
