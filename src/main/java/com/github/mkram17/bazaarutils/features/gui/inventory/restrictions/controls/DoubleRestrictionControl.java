package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigObject
public final class DoubleRestrictionControl implements RestrictionControl<NumericRestrictBy> {
    @ConfigEntry(
            id = "enabled",
            translation = "bazaarutils.config.inventory.restrictions.control.enabled.label"
    )
    public boolean enabled;

    @ConfigEntry(
            id = "targets",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.label"
    )
    @Comment(
            value = "The features for which this rule is enabled",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.hint"
    )
    public RestrictionTarget[] targets = new RestrictionTarget[] {
            RestrictionTarget.INSTANT_SELL,
            RestrictionTarget.SELL_SACKS
    };

    @ConfigEntry(
            id = "rule",
            translation = "bazaarutils.config.inventory.restrictions.control.numeric.type.label"
    )
    @Comment(
            value = "Whether the restriction triggers on total coin value or item quantity",
            translation = "bazaarutils.config.inventory.restrictions.control.numeric.type.hint"
    )
    public NumericRestrictBy rule;

    @ConfigEntry(
            id = "amount",
            translation = "bazaarutils.config.inventory.restrictions.control.numeric.threshold.label"
    )
    @Comment(
            value = "The threshold value above which the restriction will trigger",
            translation = "bazaarutils.config.inventory.restrictions.control.numeric.threshold.hint"
    )
    public double amount;

    public DoubleRestrictionControl(boolean enabled, NumericRestrictBy rule, double amount) {
        this.enabled = enabled;
        this.rule = rule;
        this.amount = amount;
    }

    @Override
    public boolean shouldRestrict(OrderInfo item) {
        return switch (rule) {
            case PRICE -> item.getPricePerItem() * item.getVolume() > amount;
            case VOLUME -> item.getVolume() > amount;
        };
    }

    @Override
    public String describeRule() {
        return switch (rule) {
            case PRICE  -> "PRICE: "  + amount;
            case VOLUME -> "VOLUME: " + amount;
        };
    }
}