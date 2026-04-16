package com.github.mkram17.bazaarutils.utils.bazaar.data;

public record PriceLevel(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        long timestamp,
        DataSources source
) {
    /** Optimistic increment when a new user order is placed at this level. */
    public PriceLevel withPlacementIncrement(int amount, long now) {
        return new PriceLevel(pricePerUnit, totalVolume + amount, orderCount + 1, now, new DataSources.OrderPlaced(now));
    }

    /**
     * Decrements volume and stamps the mutating source so eviction and
     * supersession rules remain coherent after the mutation.
     */
    public PriceLevel withVolumeDecrement(long amount, DataSources.UserPositionEvent mutatingSource) {
        return new PriceLevel(pricePerUnit, Math.max(0, totalVolume - amount), orderCount, mutatingSource.timestamp(), mutatingSource);
    }

    /**
     * Returns {@code true} when {@code incoming} should replace this level wholesale.
     *
     * <p>Called exclusively from {@link ProductData#apply} — {@code incoming} is
     * always a {@link DataSources.Snapshot}.
     *
     * <ul>
     *   <li>{@link DataSources.PageSummary} — direct, frequent screen read; always
     *       authoritative for the levels it explicitly observes.</li>
     *   <li>{@link DataSources.ApiSnapshot} — supersedes any level with a strictly
     *       older timestamp, regardless of whether that level originated from a
     *       prior snapshot or a user-position event.</li>
     * </ul>
     */
    public boolean isSupersededBy(PriceLevel incoming) {
        if (!(incoming.source() instanceof DataSources.Snapshot)) return false;
        if (incoming.source() instanceof DataSources.PageSummary) return true;
        // ApiSnapshot: wins over any strictly older level
        return incoming.source().timestamp() > this.source().timestamp()
                && (incoming.totalVolume() != this.totalVolume()
                || incoming.orderCount() != this.orderCount());
    }

    /**
     * Returns {@code true} when a point-mutation event should be allowed to
     * increment or decrement this level.
     *
     * <p>Called from {@link ProductData#place} and {@link ProductData#decrement}.
     * The incoming source is always a {@link DataSources.UserPositionEvent}.
     * The event must be at least as new as the level's current timestamp to
     * prevent out-of-order replays from rolling back newer state.
     */
    public boolean acceptsUpdateFrom(DataSources.UserPositionEvent incoming) {
        return incoming.timestamp() >= this.source().timestamp();
    }
}