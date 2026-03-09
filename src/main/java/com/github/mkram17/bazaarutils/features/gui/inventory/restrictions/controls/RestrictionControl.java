package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;

public sealed interface RestrictionControl<T extends Enum<T>> extends Restrictor permits DoubleRestrictionControl, StringRestrictionControl {
    boolean isEnabled();
    RestrictionTarget[] getTargets();
    T getRule();
    String describeRule();

    default boolean appliesTo(RestrictionTarget target) {
        for (RestrictionTarget scoped : getTargets()) {
            if (scoped == target) return true;
        }

        return false;
    }
}