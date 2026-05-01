package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.data.MayorPerks;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.info.MayorChangeEvent;

@Module
public class TaxContext extends BUListener {

    private static volatile Boolean cachedQuadTaxes = null;

    @Subscription(priority = Priority.FIRST)
    public void onMayorChange(MayorChangeEvent event) {
        cachedQuadTaxes = event.getMayor().getActivePerks().contains(MayorPerks.INSTANCE.getQUAD_TAXES());
    }

    public static boolean isQuadTaxes() {
        if (cachedQuadTaxes == null) {
            cachedQuadTaxes = MayorPerks.INSTANCE.getQUAD_TAXES().getActive();
        }

        return cachedQuadTaxes;
    }

    /**
     * {@code key}'s recorded tier's base tax percent, multiplied by 4 when Quad Taxes is
     * active. Use everywhere tax is <em>applied</em> (sell matching, coin back-calculation,
     * etc). Tier is per-profile — see {@code UserOrdersStorage.ProfileData.bazaarFlipperTier}
     * — Quad Taxes isn't; it's a Mayor perk, the same for every profile at any given moment,
     * which is why only this half of the calculation takes a key.
     */
    public static double effectiveTaxPercent(@NotNull ProfileKey key) {
        double base = UserOrdersStorage.get(key).bazaarFlipperTier().getUserBazaarTax();

        return isQuadTaxes() ? base * 4.0 : base;
    }

    /**
     * Strips the Quad Taxes multiplier from a raw on-screen value so it can be
     * compared against stored base tier values. Only needed in {@code reconcileTax}.
     */
    public static double normalizeObserved(double observedPercent) {
        return isQuadTaxes() ? observedPercent / 4.0 : observedPercent;
    }
}