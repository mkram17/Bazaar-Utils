package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

/**
 * Immutable snapshot of volume and order count at a single price point in the order book.
 * Carries the {@link BazaarDataOrigin} of the last write so that supersession and eviction
 * rules in {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData} remain coherent
 * across concurrent sources.
 */
public record PriceLevel(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        BazaarDataOrigin origin
) {
    /** Returns a copy with {@code amount} added to volume and order count incremented by one. */
    public PriceLevel withPlacementIncrement(int amount, long now) {
        return new PriceLevel(pricePerUnit, totalVolume + amount, orderCount + 1, new BazaarDataOrigin.OrderPlaced(now));
    }

    /**
     * Returns a copy with volume decremented by {@code amount}, stamped with {@code source}.
     * When {@code terminal} is {@code true}, order count is also decremented by one.
     */
    public PriceLevel withVolumeDecrement(long amount, boolean terminal, BazaarDataOrigin.UserPositionEvent source) {
        int newCount = terminal ? Math.max(0, orderCount - 1) : orderCount;

        return new PriceLevel(pricePerUnit, Math.max(0, totalVolume - amount), newCount, source);
    }

    /**
     * Returns {@code true} when the bounded-walk loop should stop at this level.
     * For INSTABUY (ascending asks): stops when {@code pricePerUnit > boundary}.
     * For INSTASELL (descending bids): stops when {@code pricePerUnit < boundary}.
     */
    public boolean exceedsBoundary(PriceType type, double boundary) {
        return type == PriceType.INSTABUY ? pricePerUnit > boundary : pricePerUnit < boundary;
    }

    /**
     * Returns {@code true} when {@code incoming} — always a {@link BazaarDataOrigin.Snapshot} —
     * should replace this level.
     *
     * <p>{@link BazaarDataOrigin.PageSummary} always wins for levels it explicitly observes.
     * {@link BazaarDataOrigin.ApiSnapshot} wins only when its timestamp is strictly newer and
     * the volume or order count differs — same-data updates are skipped to avoid spurious
     * change signals.
     */
    public boolean isSupersededBy(PriceLevel incoming) {
        if (!(incoming.origin() instanceof BazaarDataOrigin.Snapshot)) return false;
        if (incoming.origin() instanceof BazaarDataOrigin.PageSummary) return true;
        // ApiSnapshot: supersedes any strictly older level whose data changed.
        // Same-timestamp or data-identical levels are left in place to avoid spurious updates.
        return incoming.origin().timestamp() > this.origin().timestamp()
                && (incoming.totalVolume() != this.totalVolume()
                || incoming.orderCount() != this.orderCount());
    }

    /**
     * Returns {@code true} when {@code incoming} is at least as recent as this level's last
     * write, preventing out-of-order event replay from rolling back newer state.
     */
    public boolean acceptsUpdateFrom(BazaarDataOrigin.UserPositionEvent incoming) {
        return incoming.timestamp() >= this.origin().timestamp();
    }
}