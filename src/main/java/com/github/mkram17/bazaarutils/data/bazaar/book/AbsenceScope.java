package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The region of prices a book mutation proved empty on one side of a product,
 * used by {@code FillInference} and {@link ProductData} to decide which unmentioned
 * prices may safely be evicted or inferred as filled.
 */
public sealed interface AbsenceScope
        permits AbsenceScope.None,
        AbsenceScope.BetterThan,
        AbsenceScope.Window {

    /** Whether {@code price} falls within the region this scope proved absent, under {@code type}'s direction. */
    boolean contains(PriceType type, double price);

    /** No claim. The default for a mutation that proved nothing about absence. */
    record None() implements AbsenceScope {
        @Override
        public boolean contains(PriceType type, double price) {
            return false;
        }
    }

    AbsenceScope NONE = new None();

    /**
     * Every price strictly better than {@code boundary}: what an unbounded
     * {@link ProductData.WalkOp#ahead} drain produces. A direct claim about the
     * whole region, not merely about volume — see that factory's own doc for why a
     * chat-confirmed fill has standing to make it.
     */
    record BetterThan(double boundary) implements AbsenceScope {
        @Override
        public boolean contains(PriceType type, double price) {
            return type.strictlyBetter(price, boundary);
        }
    }

    /**
     * What one splice of one side actually proved about that side's coverage — enough
     * to decide, for any given price, whether this read has standing to call it
     * absent. {@code lo}/{@code hi} are the observed price extremes; both are
     * {@code null} for an empty read. {@code type} is supplied at {@link #contains}
     * time, not stored here — the same lo/hi pair says nothing about direction on its own.
     */
    record Window(
            boolean exhaustive,
            @Nullable Double lo,
            @Nullable Double hi
    ) implements AbsenceScope {

        public Window {
            if ((lo == null) != (hi == null)) {
                throw new IllegalArgumentException(
                        "Window lo and hi must both be null or both be non-null: lo=" + lo + ", hi=" + hi);
            }
            if (lo != null && lo > hi) {
                throw new IllegalArgumentException(
                        "Window lo (%s) must not exceed hi (%s)".formatted(lo, hi));
            }
        }

        /** Derives coverage from one splice's reported levels without intermediate allocations. */
        public static <O extends BazaarDataOrigin.Snapshot> Window of(
                O origin,
                List<PriceLevel<O>> incoming
        ) {
            boolean exhaustive = origin.isExhaustive(incoming.size());

            if (incoming.isEmpty()) {
                return new Window(exhaustive, null, null);
            }

            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (PriceLevel<O> level : incoming) {
                double p = level.pricePerUnit();
                if (p < lo) lo = p;
                if (p > hi) hi = p;
            }

            return new Window(exhaustive, lo, hi);
        }

        /**
         * Returns {@code true} when this coverage has standing to call {@code price} absent
         * if it wasn't in the splice:
         * <ul>
         *   <li><b>Exhaustive read</b> (fewer than {@code maxDepth} levels): proves every price
         *       on the side is accounted for, so any unmentioned price is absent.</li>
         *   <li><b>Partial read</b>: proves the contiguous top of book down through
         *       {@code worstReported} ({@code lo} on bids, {@code hi} on asks), so any
         *       unmentioned price at least as good as {@code worstReported} is proven gone;
         *       anything strictly worse lies beyond the shallow read's horizon.</li>
         * </ul>
         */
        @Override
        public boolean contains(PriceType type, double price) {
            if (exhaustive) return true;
            if (lo == null || hi == null) return false;

            double worstReported = type.higherIsBetter() ? lo : hi;

            return type.atLeastAsGood(price, worstReported);
        }
    }
}
