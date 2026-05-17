package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;

public sealed interface RestrictionControl<T extends Enum<T>> extends Restrictor permits DoubleRestrictionControl, StringRestrictionControl {
    RestrictionTarget[] getTargets();
    T getRule();
    String describeRule();

    default boolean appliesTo(RestrictionTarget target) {
        for (RestrictionTarget scoped : getTargets()) {
            if (scoped == target) return true;
        }

        return false;
    }

    default String formatTargets() {
        RestrictionTarget[] targets = getTargets();

        if (targets == null || targets.length == 0) return "None";

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < targets.length; i++) {
            String pretty = switch (targets[i]) {
                case INSTANT_SELL -> "Instant Sell";
                case SELL_SACKS -> "Sell Sacks";
                case BUY_ORDER -> "Buy Order";
                case SELL_OFFER -> "Sell Offer";
            };

            builder.append(pretty);

            if (i < targets.length - 1) {
                builder.append(", ");
            }
        }

        return builder.toString();
    }
}