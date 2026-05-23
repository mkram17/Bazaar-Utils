package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public sealed interface AbsenceScope
        permits AbsenceScope.None,
        AbsenceScope.Exact,
        AbsenceScope.BetterThan,
        AbsenceScope.Window {

    /** Whether {@code price} falls within the region this scope proved absent, under {@code type}'s direction. */
    boolean contains(PriceType type, double price);

    /** No claim. The default for a mutation that proved nothing about absence. */
    record None() implements AbsenceScope {
        @Override
        public boolean contains(PriceType type, double price) { return false; }
    }

    AbsenceScope NONE = new None();

    /**
     * Exactly one price: what a terminal {@code Decrement} produces. The claim
     * reaches only the price that decrement actually touched — not the level's other
     * occupants, not any other price on the product.
     */
    record Exact(double price) implements AbsenceScope {
        @Override
        public boolean contains(PriceType type, double candidate) {
            return Double.compare(price, candidate) == 0;
        }
    }

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
     * {@code null} for an empty, non-exhaustive read that proved nothing at all.
     * {@code type} is supplied at {@link #contains} time, not stored here — the same
     * lo/hi pair says nothing about direction on its own.
     */
    record Window(
            boolean exhaustive,
            @Nullable Double lo,
            @Nullable Double hi
    ) implements AbsenceScope {

        /** Derives coverage from one splice's reported levels. */
        public static <O extends BazaarDataOrigin.Snapshot> Window of(
                O origin,
                List<PriceLevel<O>> incoming
        ) {
            boolean exhaustive = origin.isExhaustive(incoming.size());

            if (incoming.isEmpty()) {
                return new Window(exhaustive, null, null);
            }

            var prices = incoming.stream()
                    .map(PriceLevel::pricePerUnit)
                    .toList();

            double lo = Collections.min(prices);
            double hi = Collections.max(prices);

            return new Window(exhaustive, lo, hi);
        }

        /**
         * Returns {@code true} when this coverage has standing to call {@code price} absent
         * if it wasn't in the splice.
         *
         * <ul>
         * <li><b>Exhaustive authorization:</b> If a snapshot returns fewer levels than its maximum
         * capacity, it proves it reached the absolute bottom of the book. It authorizes any price,
         * unconditionally, across the entire side.</li>
         * <li><b>Window authorization:</b> The snapshot proves the exact state of the book between
         * its best and worst reported prices. Any price that falls within this observed {@code [lo, hi]}
         * band is authorized; because the snapshot is contiguous, an unmentioned price in this range
         * is proven gone.</li>
         * <li><b>Superior price authorization:</b> The snapshot establishes the absolute best price
         * currently available. Since the read reports from the top of the book downwards, any price
         * strictly better than {@code bestReported} would have displaced the reported levels. Its
         * absence proves it is gone.</li>
         * </ul>
         *
         * <p>A price worse than everything a partial read bothered to report proves nothing—it is
         * simply beyond what the shallow read looked at, and therefore not authorized for eviction.
         *
         * @param type the direction of the market (instabuy/instasell) to evaluate price superiority
         * @param price the historical or tracked price being checked against this coverage
         * @return {@code true} if this coverage definitively proves the absence of the given price
         */
        @Override
        public boolean contains(PriceType type, double price) {
            // Empty snapshot (no levels returned)
            if (lo == null || hi == null) {
                return exhaustive;
            }

            // 1. Window authorization
            if (price >= lo && price <= hi) return true;

            // 2. Superior price authorization
            double bestReported = type.higherIsBetter() ? hi : lo;
            if (type.strictlyBetter(price, bestReported)) {
                return true;
            }

            // 3. Exhaustive authorization (Inferior prices)
            return exhaustive;
        }
    }
}
