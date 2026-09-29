package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.Util;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Capability for a priced order line — a per-unit price plus the volume it was priced against —
 * to be compared with tolerance instead of exact equality.
 *
 * <p>Hypixel rounds displayed totals once total order value passes {@link #FOLDING_THRESHOLD}
 * (10 000 coins), introducing up to {@link #COIN_EPSILON} (0.9 coins) of per-unit error.
 * {@link #computeTolerance} derives the per-unit band from those constants and the volume;
 * {@link #tolerance()} and {@link #isPriceSimilarTo} expose it per-instance.
 *
 * <p>Implementors supply only {@link #pricePerItem()} and {@link #volume()} — usually a direct
 * delegation to an existing field or accessor. Everything else is derived here once.
 */
public interface PriceQuantity {
    /** Maximum absolute coin error Hypixel's half-up total rounding can introduce into a displayed order total. Per-unit bound is {@code COIN_EPSILON / volume}. */
    double COIN_EPSILON = 0.5;

    /** Total order value above which Hypixel starts rounding displayed prices. */
    double FOLDING_THRESHOLD = 10_000;

    double pricePerItem();

    int volume();

    default boolean priceIsExact() {
        return tolerance() == 0.0;
    }

    /**
     * Per-unit tolerance band for this instance's {@link #pricePerItem()} and {@link #volume()}.
     * Recomputed on every call by default — cheap, but override to return a cached value where
     * the instance is immutable and can price it once up front.
     */
    default double tolerance() {
        return computeTolerance(pricePerItem(), volume());
    }

    /**
     * Computes the per-unit pricePerUnit tolerance for this order.
     *
     * <p>When the total order value is below {@link #FOLDING_THRESHOLD}, Hypixel does not
     * round, so tolerance is 0. Above the threshold, up to {@link #COIN_EPSILON} coins
     * of rounding can appear in the total, which translates to a per-unit tolerance of
     * {@code COIN_EPSILON / volume}.
     */
    static double computeTolerance(double price, int volume) {
        if (volume <= 0 || price <= 0) return COIN_EPSILON;
        if (price * volume < FOLDING_THRESHOLD) return 0.0;

        return COIN_EPSILON / volume;
    }

    /**
     * {@code true} when {@code other} is within tolerance of {@link #pricePerItem()}.
     * Tolerance = {@link #tolerance()} + 1% of {@code other}, absorbing floating-point residuals.
     */
    default boolean isPriceSimilarTo(double other) {
        return Util.genericIsSimilarValue(pricePerItem(), other, tolerance() + Math.ulp(other));
    }

    /** Matches any {@code T} whose {@link #pricePerItem()} is within tolerance of {@code price}. */
    static <T extends PriceQuantity> Predicate<T> similarTo(double price) {
        return it -> it.isPriceSimilarTo(price);
    }

    /**
     * Matches any {@code T} whose price is within tolerance of {@code reference}'s own price,
     * combining BOTH sides' tolerance bands (plus 1% of the candidate's price for
     * floating-point residue) — the correct combination once either side's own reading could
     * carry Hypixel's rounding error, not just the one being tested.
     */
    static <T extends PriceQuantity> Predicate<T> similarTo(@NotNull PriceQuantity reference) {
        double referenceTolerance = reference.tolerance();
        double referencePrice = reference.pricePerItem();

        return subject -> {
            double combined = referenceTolerance + subject.tolerance() + Math.ulp(subject.pricePerItem());

            return Util.genericIsSimilarValue(referencePrice, subject.pricePerItem(), combined);
        };
    }

    /** Wraps a raw {@code (price, volume)} pair */
    record Raw(double pricePerItem, int volume) implements PriceQuantity {}

    /** Wraps a {@code (price, volume)} pair whose price is known exact — zero tolerance. */
    record Exact(double pricePerItem, int volume) implements PriceQuantity {
        @Override
        public double tolerance() {
            return 0.0;
        }

        @Override
        public boolean priceIsExact() {
            return true;
        }
    }

    static PriceQuantity of(double pricePerItem, int volume, boolean exact) {
        return exact ? new Exact(pricePerItem, volume) : new Raw(pricePerItem, volume);
    }
}