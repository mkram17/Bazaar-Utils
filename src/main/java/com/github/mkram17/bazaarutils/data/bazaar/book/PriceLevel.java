package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

/**
 * One price's aggregate state in the order book: total volume, the number of
 * distinct orders resting there, and the {@link BazaarDataOrigin} of whichever
 * write most recently confirmed those figures.
 */
public record PriceLevel(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        BazaarDataOrigin origin
) {
    /**
     * Returns a copy with {@code amount} added to volume and the order count
     * raised by one, stamped with a fresh {@link BazaarDataOrigin.OrderPlaced}
     * at {@code now} — the confirmation that one more order now occupies this
     * price.
     */
    public PriceLevel withPlacementIncrement(int amount, long now) {
        return new PriceLevel(pricePerUnit, totalVolume + amount, orderCount + 1, new BazaarDataOrigin.OrderPlaced(now));
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
    public PriceLevel withVolumeDecrement(long amount, boolean terminal, BazaarDataOrigin.UserPositionEvent source) {
        int newCount = terminal ? Math.max(0, orderCount - 1) : orderCount;

        return new PriceLevel(pricePerUnit, Math.max(0, totalVolume - amount), newCount, source);
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
    public boolean agreesWith(PriceLevel other) {
        return this.totalVolume() == other.totalVolume() && this.orderCount() == other.orderCount();
    }

    /**
     * Returns {@code true} when {@code incoming} — always a {@link BazaarDataOrigin.Snapshot}
     * — should replace this entry in base.
     *
     * <p>{@link BazaarDataOrigin.Snapshot#outranks} is the standing gate: a read only
     * contends for this price once it has standing over whoever wrote it last. Once
     * standing holds, a {@link BazaarDataOrigin.PageSummary} wins outright — a newer
     * render is simply the more current account of what it shows. An
     * {@link BazaarDataOrigin.ApiSnapshot} is held to a tighter bar than {@code outranks}
     * alone provides: {@code outranks} permits an equal timestamp for a zero-grace source,
     * but this method still requires a strictly newer timestamp and, via
     * {@link #agreesWith}, an actual substantive change — otherwise a same-timestamp or
     * identical re-poll would count as superseding when nothing really changed.
     */
    public boolean isSupersededBy(PriceLevel incoming) {
        if (!(incoming.origin() instanceof BazaarDataOrigin.Snapshot snap)) return false;
        if (!snap.outranks(this.origin())) return false;
        if (snap instanceof BazaarDataOrigin.PageSummary) return true;

        return incoming.origin().timestamp() > this.origin().timestamp() && !this.agreesWith(incoming);
    }
}