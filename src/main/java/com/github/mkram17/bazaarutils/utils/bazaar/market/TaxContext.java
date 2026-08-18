package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.data.stored.ProfileFlagsStorage;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.data.MayorPerks;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.info.MayorChangeEvent;

import java.util.concurrent.atomic.AtomicLong;

@Module
public class TaxContext extends BUListener {

    private static volatile Boolean cachedQuadTaxes = null;

    private static final AtomicLong LAST_TAX_WARNING_MS = new AtomicLong(0L);
    private static final long TAX_WARN_COOLDOWN_MS = 60_000L;

    @Subscription(priority = Priority.FIRST)
    public void onMayorChange(MayorChangeEvent event) {
        var mayor = event.getMayor();
        cachedQuadTaxes = mayor != null && mayor.getActivePerks().contains(MayorPerks.INSTANCE.getQUAD_TAXES());
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
     * etc). Tier is per-profile — see {@link ProfileFlagsStorage.Data#bazaarFlipperTier()}.
     */
    public static double effectiveTaxPercent(@NotNull ProfileKey key) {
        double base = ProfileFlagsStorage.get(key).bazaarFlipperTier().getUserBazaarTax();

        return isQuadTaxes() ? base * 4.0 : base;
    }

    /**
     * Strips the Quad Taxes multiplier from a raw on-screen value so it can be
     * compared against stored base tier values. Only needed in {@link #reconcileObservedTax}.
     */
    public static double normalizeObserved(double observedPercent) {
        return isQuadTaxes() ? observedPercent / 4.0 : observedPercent;
    }

    /**
     * Matches an observed product-page tax percentage against {@link PlayerAccountUpgrades.BazaarFlipper}
     * tiers (after stripping Quad Taxes if active) and persists any tier change for {@code key}.
     */
    public static void reconcileObservedTax(double observedPercent, @NotNull ProfileKey key) {
        double normalizedTax = normalizeObserved(observedPercent);
        var currentTier = ProfileFlagsStorage.get(key).bazaarFlipperTier();

        for (PlayerAccountUpgrades.BazaarFlipper tier : PlayerAccountUpgrades.BazaarFlipper.values()) {
            if (Math.abs(tier.getUserBazaarTax() - normalizedTax) < 0.01) {
                if (currentTier != tier) {
                    Util.logMessage("reconcileObservedTax: %s → %s (observed %.4g%%%s)"
                            .formatted(currentTier, tier, observedPercent, isQuadTaxes() ? " [quad taxes /4 → " + normalizedTax + "%]" : ""));

                    ProfileFlagsStorage.markBazaarFlipperTier(key, tier);
                    PlayerActionUtil.notifyAll("Bazaar Flipper tier auto-detected as %s from observed tax.".formatted(tier.name()));
                }

                return;
            }
        }

        Util.logMessage("reconcileObservedTax: observed %.4g%% (normalized %.4g%%) matches no BazaarFlipper tier — ignoring"
                .formatted(observedPercent, normalizedTax));
    }

    public static void warnTaxMisconfiguration(String context) {
        long now = System.currentTimeMillis();
        long prev = LAST_TAX_WARNING_MS.get();

        if (now - prev < TAX_WARN_COOLDOWN_MS) return;
        if (!LAST_TAX_WARNING_MS.compareAndSet(prev, now)) return;

        Util.notifyError(context + " Open any Bazaar product page with sellable items in your inventory to auto-detect your Bazaar Flipper tier.", null);
    }
}