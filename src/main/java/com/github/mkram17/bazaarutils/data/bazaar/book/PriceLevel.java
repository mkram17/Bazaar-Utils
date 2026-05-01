package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;

public record PriceLevel(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        BazaarDataOrigin origin
) {
    /** Optimistic increment when a new user order is placed at this level. */
    public PriceLevel withPlacementIncrement(int amount, long now) {
        return new PriceLevel(pricePerUnit, totalVolume + amount, orderCount + 1, new BazaarDataOrigin.OrderPlaced(now));
    }

    /**
     * Decrements volume and stamps the mutating origin so eviction and
     * supersession rules remain coherent after the mutation.
     */
    public PriceLevel withVolumeDecrement(long amount, boolean terminal, BazaarDataOrigin.UserPositionEvent source) {
        int newCount = terminal ? Math.max(0, orderCount - 1) : orderCount;

        return new PriceLevel(pricePerUnit, Math.max(0, totalVolume - amount), newCount, source);
    }

    /**
     * Returns {@code true} when this level lies outside the walk boundary for the
     * given book direction — i.e., the bounded consume loop should stop here.
     *
     * INSTABUY  (ascending asks):  exceeded when pricePerUnit > boundary
     * INSTASELL (descending bids): exceeded when pricePerUnit < boundary
     */
    public boolean exceedsBoundary(PriceType type, double boundary) {
        return type == PriceType.INSTABUY ? pricePerUnit > boundary : pricePerUnit < boundary;
    }

    /**
     * Returns {@code true} when {@code incoming} should replace this level wholesale.
     *
     * <p>Called exclusively from {@link ProductData#apply} — {@code incoming} is
     * always a {@link BazaarDataOrigin.Snapshot}.
     *
     * <ul>
     *   <li>{@link BazaarDataOrigin.PageSummary} — direct, frequent screen read; always
     *       authoritative for the levels it explicitly observes.</li>
     *   <li>{@link BazaarDataOrigin.ApiSnapshot} — supersedes any level with a strictly
     *       older timestamp, regardless of whether that level originated from a
     *       prior snapshot or a user-position event.</li>
     * </ul>
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
     * Returns {@code true} when a point-mutation event should be allowed to
     * increment or decrement this level.
     *
     * <p>Called from {@link ProductData#place} and {@link ProductData#decrement}.
     * The incoming origin is always a {@link BazaarDataOrigin.UserPositionEvent}.
     * The event must be at least as new as the level's current timestamp to
     * prevent out-of-order replays from rolling back newer state.
     */
    public boolean acceptsUpdateFrom(BazaarDataOrigin.UserPositionEvent incoming) {
        return incoming.timestamp() >= this.origin().timestamp();
    }
}