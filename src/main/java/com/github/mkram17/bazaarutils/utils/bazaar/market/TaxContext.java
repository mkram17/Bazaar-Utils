package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
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
     * The configured tier's base tax percent, multiplied by 4 when Quad Taxes is active.
     * Use everywhere tax is <em>applied</em> (sell matching, coin back-calculation, etc).
     */
    public static double effectiveTaxPercent() {
        double base = BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.getUserBazaarTax();

        return isQuadTaxes() ? base * 4.0 : base;
    }

    /**
     * Strips the Quad Taxes multiplier from a raw on-screen value so it can be
     * compared against stored base tier values. Only needed in {@code reconcileTax}.
     */
    public static double normalizeObserved(double observedPercent) {
        return isQuadTaxes() ? observedPercent / 4.0 : observedPercent;
    }

    private static volatile long lastTaxWarningMs = 0L;
    private static final long TAX_WARN_COOLDOWN_MS = 60_000L;

    public static void warnTaxMisconfiguration(String context) {
        long now = System.currentTimeMillis();

        if (now - lastTaxWarningMs < TAX_WARN_COOLDOWN_MS) return;
        lastTaxWarningMs = now;

        Util.notifyError(context + " Run /bu config to fix your Account Upgrade setting.", new Throwable());
    }
}