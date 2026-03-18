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
public final class StringRestrictionControl implements RestrictionControl<StringRestrictBy>, ListEntryInfoProvider {
    @ConfigEntry(
            id = "targets",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.label"
    )
    @Comment(
            value = "The features for which this rule is active",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.hint"
    )
    public RestrictionTarget[] targets = new RestrictionTarget[] {
            RestrictionTarget.INSTANT_SELL,
            RestrictionTarget.SELL_SACKS
    };

    @ConfigEntry(
            id = "name",
            translation = "bazaarutils.config.inventory.restrictions.control.name.value.label"
    )
    @Comment(
            value = "The item name that, if present, will trigger the restriction",
            translation = "bazaarutils.config.inventory.restrictions.control.name.value.hint"
    )
    public String name;

    private StringRestrictBy rule = StringRestrictBy.NAME;

    public StringRestrictionControl(String name) {
        this.name = name;
    }

    @Override
    public boolean shouldRestrict(OrderInfo container) {
        return container.getName().equalsIgnoreCase(name);
    }

    @Override
    public String describeRule() {
        return "NAME: " + name;
    }

    @Override
    public Text getTitle(int index) {
        return Text.literal("Blocks items matching \"" + name + "\"");
    }

    @Override
    public Text getDescription(int index) {
        return Text.literal("Applies to: " + formatTargets());
    }
}