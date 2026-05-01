package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.Util;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Capability for a priced order line — a per-unit price plus the volume it was priced
 * against — to be compared with tolerance instead of exact equality.
 *
 * <p>Hypixel rounds a displayed order total to the nearest whole coin once its value
 * passes {@link #FOLDING_THRESHOLD} (10,000 coins), introducing up to
 * {@link #COIN_EPSILON} (0.5 coins) of error on that total. {@link #computeTolerance}
 * turns that total-level bound into a per-unit one by dividing across the volume;
 * {@link #tolerance()} and {@link #isPriceSimilarTo} expose it per instance.
 *
 * <p>Implementors supply only {@link #pricePerItem()} and {@link #volume()} — usually a
 * direct delegation to an existing field. Everything else is derived here.
 */
public interface PriceQuantity {
    /** Maximum coin error rounding a total to the nearest whole coin can introduce. Per-unit bound is {@code COIN_EPSILON / volume}. */
    double COIN_EPSILON = 0.5;

    /** Total order value above which Hypixel starts rounding displayed prices. */
    double FOLDING_THRESHOLD = 10_000;

    double pricePerItem();

    int volume();

    /** {@code true} when {@link #tolerance()} is zero — this instance's price is known exact. */
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
     * Computes the per-unit tolerance for a given price and volume. Zero below
     * {@link #FOLDING_THRESHOLD} total value, where Hypixel doesn't round; otherwise
     * {@link #COIN_EPSILON} divided across the volume. A non-positive price or volume
     * is treated as maximally uncertain — the full, undivided {@code COIN_EPSILON}.
     */
    static double computeTolerance(double price, int volume) {
        if (volume <= 0 || price <= 0) return COIN_EPSILON;
        if (price * volume < FOLDING_THRESHOLD) return 0.0;

        return COIN_EPSILON / volume;
    }

    /**
     * {@code true} when {@code other} is within tolerance of {@link #pricePerItem()}.
     * Tolerance is {@link #tolerance()} plus a floating-point residue guard
     * ({@link Math#ulp}) — not a percentage of {@code other}.
     */
    default boolean isPriceSimilarTo(double other) {
        return Util.genericIsSimilarValue(pricePerItem(), other, tolerance() + Math.ulp(other));
    }

    /** Matches any {@code T} whose {@link #pricePerItem()} is within tolerance of {@code price}. */
    static <T extends PriceQuantity> Predicate<T> similarTo(double price) {
        return it -> it.isPriceSimilarTo(price);
    }

    /**
     * Matches any {@code T} whose price is within tolerance of {@code reference}'s own
     * price, combining both sides' tolerance bands plus a floating-point residue guard
     * — the correct combination once either side's own reading could carry Hypixel's
     * rounding error, not just the one being tested.
     */
    static <T extends PriceQuantity> Predicate<T> similarTo(@NotNull PriceQuantity reference) {
        double referenceTolerance = reference.tolerance();
        double referencePrice = reference.pricePerItem();

        return subject -> {
            double combined = referenceTolerance + subject.tolerance() + Math.ulp(subject.pricePerItem());

            return Util.genericIsSimilarValue(referencePrice, subject.pricePerItem(), combined);
        };
    }

    /** Wraps a raw {@code (price, volume)} pair. */
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

    /** Returns an {@link Exact} or {@link Raw} wrapper depending on {@code exact}. */
    static PriceQuantity of(double pricePerItem, int volume, boolean exact) {
        return exact ? new Exact(pricePerItem, volume) : new Raw(pricePerItem, volume);
    }
}