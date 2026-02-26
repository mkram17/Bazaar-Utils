package com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.NumericRestrictBy;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigObject
public final class DoubleSellRestrictionControl implements SellRestrictionControl<NumericRestrictBy> {

    @ConfigEntry(
            id = "enabled",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.restriction.enabled.label"
    )
    public boolean enabled;

    @ConfigEntry(
            id = "rule",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.restriction.numeric.label"
    )
    @Comment(
            value = "Whether the restriction will be on total coins worth or items held",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.restriction.numeric.hint"
    )
    public NumericRestrictBy rule;

    @ConfigEntry(
            id = "amount",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.restriction.amount.label"
    )
    @Comment(
            value = "The amount of coins or items held to lock upon",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.restriction.amount.hint"
    )
    public double amount;

    public DoubleSellRestrictionControl(boolean enabled, NumericRestrictBy rule, double amount) {
        this.enabled = enabled;
        this.rule = rule;
        this.amount = amount;
    }

    @Override
    public boolean shouldRestrict(OrderInfo container) {
        return container.getPricePerItem() > amount;
    }
}