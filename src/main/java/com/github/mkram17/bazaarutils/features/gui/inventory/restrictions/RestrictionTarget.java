package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.text.Text;

public enum RestrictionTarget implements TooltipProvider, Translatable {
    INSTANT_SELL {
        @Override
        public String getTranslationKey() {
            return "bazaarutils.config.inventory.restrictions.target.instant_sell.label";
        }

        @Override
        public Text getTooltip() {
            return Text.translatable("bazaarutils.config.inventory.restrictions.target.instant_sell.label");
        }
    };

    public abstract String getTranslationKey();

    public static boolean isRestrictorFeatureEnabled(RestrictionTarget target) {
        for (RestrictionTarget scoped : InventoryConfig.RESTRICTIONS_ENABLED_FEATURES) {
            if (scoped == target) return true;
        }

        return false;
    }
}