package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;

@Getter
@Setter
@ConfigObject
public final class StringRestrictionControl implements RestrictionControl<StringRestrictBy>, ListEntryInfoProvider {
    @ConfigEntry(
            id = "name",
            translation = "bazaarutils.config.inventory.restrictions.control.name.value.label"
    )
    @Comment(
            value = "The item name that, if present, will trigger the restriction",
            translation = "bazaarutils.config.inventory.restrictions.control.name.value.hint"
    )
    public String name;

    @ConfigEntry(
            id = "targets",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.label"
    )
    @Comment(
            value = "The features for which this rule is active",
            translation = "bazaarutils.config.inventory.restrictions.control.targets.hint"
    )
    public RestrictionTarget[] targets;

    private StringRestrictBy rule = StringRestrictBy.NAME;

    public StringRestrictionControl(String name, RestrictionTarget[] targets) {
        this.name = name;
        this.targets = targets;
    }

    public StringRestrictionControl() {
        this("", new RestrictionTarget[]{});
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
    public Component getTitle(int index) {
        return Component.literal("Blocks items matching \"" + name + "\"");
    }

    @Override
    public Component getDescription(int index) {
        return Component.literal("Applies to: " + formatTargets());
    }
}