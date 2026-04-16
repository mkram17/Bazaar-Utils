package com.github.mkram17.bazaarutils.utils.bazaar.data;

public sealed interface DataSources permits DataSources.Snapshot, DataSources.UserPositionEvent {
    long timestamp();

    /**
     * An authoritative read of the live market order book.
     * Absent price levels within the source's observed scope are evictions.
     */
    sealed interface Snapshot extends DataSources permits ApiSnapshot, PageSummary {}

    /** Full-depth book render, polled from the HTTP endpoint (~60 s cache). */
    record ApiSnapshot(long snapshotTs) implements Snapshot {
        public long timestamp() { return snapshotTs; }
    }

    /**
     * Per-product top-N (7) price levels read directly from the in-game UI.
     * Polled whenever the player is on a product page (~5 s cache).
     * More current than ApiSnapshot; authoritative for the levels it explicitly lists.
     */
    record PageSummary(long observedAt) implements Snapshot {
        public long timestamp() { return observedAt; }
    }

    // ── User-position events ───────────────────────────────────────────────────

    /**
     * An event driven by the user's interaction with their orders or the market.
     * Each instance asserts exactly one point mutation (increment or decrement)
     * against a specific price level, or a reconciliation of user positions.
     */
    sealed interface UserPositionEvent extends DataSources
            permits OrderPlaced, OrderFilled, OrderCancelled, OrderFlipped,
            OrderClaim, InstantDeal, OrdersScreen {}

    /** User placed a new buy/sell order. Increments the corresponding level. */
    record OrderPlaced(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** User's order was completely filled. Decrements the remaining volume at that level. */
    record OrderFilled(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** User cancelled an order. Decrements the unfilled volume at that level. */
    record OrderCancelled(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** User flipped an order. Handled by the flip data source as cancel + place. */
    record OrderFlipped(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * User claimed filled items or coins. Does NOT mutate any book level —
     * the corresponding fill decrements already occurred at fill time.
     * Present in this hierarchy only for logging and event plumbing.
     */
    record OrderClaim(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** User performed an instant buy or sell. Decrements consumed levels. */
    record InstantDeal(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * Reconciliation derived from the Orders screen.
     * Acts as a set of deltas over the user's open positions —
     * not a market snapshot, but authoritative about fill progress
     * on user-owned levels as of the screen render time.
     */
    record OrdersScreen(long observedAt) implements UserPositionEvent {
        public long timestamp() { return observedAt; }
    }

    default String describe() {
        return switch (this) {
            case ApiSnapshot snapshot -> "API snapshot @ " + snapshot.snapshotTs();
            case PageSummary summary -> "Item Summary @ " + summary.observedAt();
            case OrdersScreen screen -> "Orders screen @ " + screen.observedAt();
            case OrderPlaced placement -> "Order placed @ " + placement.confirmedAt();
            case OrderFilled filling -> "Order filled @ " + filling.confirmedAt();
            case OrderCancelled cancelment -> "Order cancelled @ " + cancelment.confirmedAt();
            case OrderFlipped flip -> "Order flipped @ " + flip.confirmedAt();
            case OrderClaim claim -> "Order claimed @ " + claim.confirmedAt();
            case InstantDeal action -> "Instant deal @ " + action.confirmedAt();
        };
    }
}