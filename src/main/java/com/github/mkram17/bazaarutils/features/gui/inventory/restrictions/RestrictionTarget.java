package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.network.chat.Component;

public enum RestrictionTarget implements TooltipProvider, Translatable {
    INSTANT_SELL,
    SELL_SACKS,
    BUY_ORDER,
    SELL_OFFER;

    @Override
    public String getTranslationKey() {
        return "bazaarutils.config.inventory.restrictions.features.target." + name().toLowerCase() + ".label";
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("bazaarutils.config.inventory.restrictions.features.target." + name().toLowerCase() + ".hint");
    }

    public static boolean isRestrictorFeatureEnabled(RestrictionTarget target) {
        for (RestrictionTarget scoped : InventoryConfig.RestrictionRules.RESTRICTIONS_ENABLED_FEATURES) {
            if (scoped == target) return true;
        }

        return false;
    }
}