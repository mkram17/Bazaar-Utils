package com.github.mkram17.bazaarutils.utils.bazaar;

import com.mojang.serialization.Codec;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import lombok.Getter;
import net.minecraft.network.chat.Component;

public class PlayerAccountUpgrades {

    public enum BazaarFlipper implements TooltipProvider, Translatable {
        NOT_UPGRADED(1.25, 14) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.not_upgraded.label";
            }
            @Override
            public Component getTooltip() {
                return Component.translatable("bazaarutils.hypixel.account_upgrades.bazaar_flipper.not_upgraded.label");
            }
        },
        FIRST_TIER(1.125, 21) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.first_tier.label";
            }
            @Override
            public Component getTooltip() {
                return Component.translatable("bazaarutils.hypixel.account_upgrades.bazaar_flipper.first_tier.label");
            }
        },
        SECOND_TIER(1, 28) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.second_tier.label";
            }
            @Override
            public Component getTooltip() {
                return Component.translatable("bazaarutils.hypixel.account_upgrades.bazaar_flipper.second_tier.label");
            }
        };

        @Getter
        public final double userBazaarTax;

        @Getter
        public final int maxBazaarOrders;

        BazaarFlipper(double userBazaarTax, int maxBazaarOrders) {
            this.userBazaarTax = userBazaarTax;
            this.maxBazaarOrders = maxBazaarOrders;
        }

        public abstract String getTranslationKey();

        public static final Codec<BazaarFlipper> CODEC = Codec.STRING.xmap(BazaarFlipper::valueOf, Enum::name);
    }
}