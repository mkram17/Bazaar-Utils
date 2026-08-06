package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.network.chat.Component;

public enum RestrictionTarget implements TooltipProvider, Translatable {
    INSTANT_SELL("instant_sell"),
    SELL_SACKS("sell_sacks");

    private final String translationKey;

    RestrictionTarget(String id) {
        this.translationKey = "bazaarutils.config.inventory.restrictions.features.target." + id + ".label";
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable(translationKey);
    }

    public static boolean isRestrictorFeatureEnabled(RestrictionTarget target) {
        for (RestrictionTarget scoped : InventoryConfig.RestrictionRules.RESTRICTIONS_ENABLED_FEATURES) {
            if (scoped == target) return true;
        }

        return false;
    }
}
