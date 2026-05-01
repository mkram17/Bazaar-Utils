package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

import java.util.List;

/**
 * One price's aggregate state in the order book: total volume, the number of
 * distinct orders resting there, and the {@link BazaarDataOrigin} of whichever
 * write most recently confirmed those figures.
 */
public record PriceLevel<O extends BazaarDataOrigin>(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        O origin
) {
    /**
     * Returns a copy with {@code amount} added to volume and the order count
     * raised by one, stamped with a fresh origin, theconfirmation
     * that one more order now occupies this price.
     */
    public PriceLevel<BazaarDataOrigin.UserPositionEvent> withPlacementIncrement(int amount, BazaarDataOrigin.UserPositionEvent origin) {
        return new PriceLevel<>(pricePerUnit, totalVolume + amount, orderCount + 1, origin);
    }

    /**
     * Returns a copy with volume reduced by {@code amount}, floored at zero,
     * and stamped with {@code source}.
     *
     * <p>{@code terminal} marks a decrement that removes an order from this
     * price entirely — a completed fill, a cancel, an expiry — as opposed to
     * an ordinary partial fill, which leaves the order still resting here.
     * Only a terminal decrement lowers the order count; a partial one moves
     * volume alone.
     */
    public PriceLevel<BazaarDataOrigin.UserPositionEvent> withVolumeDecrement(long amount, boolean terminal, BazaarDataOrigin.UserPositionEvent origin) {
        int newCount = terminal ? Math.max(0, orderCount - 1) : orderCount;

        return new PriceLevel<>(pricePerUnit, Math.max(0, totalVolume - amount), newCount, origin);
    }

    /**
     * Returns {@code true} when this level lies past {@code boundary} in the
     * direction a walk over {@code type}'s side is allowed to travel,
     * signaling that the walk should stop rather than consume it.
     */
    public boolean exceedsBoundary(PriceType type, double boundary) {
        return !type.atLeastAsGood(pricePerUnit, boundary);
    }

    /**
     * Returns {@code true} when {@code other} describes the same substantive market
     * state as this level — equal volume and equal order count. Deliberately
     * narrower than {@link #equals}, which also compares {@code pricePerUnit} and
     * the full {@link BazaarDataOrigin}: two levels can carry different origins —
     * a different poll, a different timestamp — and still agree about what the
     * market currently looks like, and it's specifically that narrower agreement,
     * not provenance, this method is for.
     *
     * <p>A genuine equivalence relation on that narrower substance: reflexive,
     * symmetric, and, being plain field comparison, trivially transitive. Unlike
     * {@link #isSupersededBy} or {@link LevelReconciliation#prevailing}, which
     * decide a winner and are not generally symmetric, this one only ever answers
     * "same or different," the same way regardless of which side calls it.
     */
    public boolean agreesWith(PriceLevel<?> other) {
        return this.totalVolume() == other.totalVolume() && this.orderCount() == other.orderCount();
    }

    /**
     * Returns {@code true} when {@code incoming} — always a {@link BazaarDataOrigin.Snapshot}
     * — should replace this entry in base.
     */
    public static boolean isSupersededBy(PriceLevel<BazaarDataOrigin.Snapshot> existing, PriceLevel<BazaarDataOrigin.Snapshot> incoming) {
        if (!incoming.origin().outranks(existing.origin())) return false;

        return incoming.origin().timestamp() > existing.origin().timestamp() && !existing.agreesWith(incoming);
    }
}