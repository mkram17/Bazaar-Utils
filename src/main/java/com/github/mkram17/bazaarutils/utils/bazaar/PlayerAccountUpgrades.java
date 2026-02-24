package com.github.mkram17.bazaarutils.utils.bazaar;

import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import lombok.Getter;
import net.minecraft.text.Text;

public class PlayerAccountUpgrades {;
    public enum BazaarFlipper implements TooltipProvider, Translatable {
        NOT_UPGRADED(1.25, 14) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.not_upgraded.value";
            }

            @Override
            public Text getTooltip() {
                return Text.of("Not Upgraded");
            }
        },

        FIRST_TIER(1.125, 21) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.first_tier.value";
            }

            @Override
            public Text getTooltip() {
                return Text.of("Bazaar Flipper I");
            }
        },

        SECOND_TIER(1, 28) {
            public String getTranslationKey() {
                return "bazaarutils.hypixel.account_upgrades.bazaar_flipper.second_tier.value";
            }

            @Override
            public Text getTooltip() {
                return Text.of("Bazaar Flipper II");
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
    }
}
