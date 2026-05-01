package com.github.mkram17.bazaarutils.data.bazaar;

/**
 * The source and timestamp behind one write to a product's order book, and,
 * through each branch's own {@code outranks} method, the standing that write
 * has to override one it disagrees with.
 *
 * <p>Every origin is one of two kinds. A {@link Snapshot} is a read of the
 * market at large — an outside account of the book, right or wrong about any
 * one player's own orders — whose standing depends on how much of the book
 * it actually covered and how far its own read can lag reality. A
 * {@link UserPositionEvent} is the player's own confirmed action — placing,
 * filling, cancelling, claiming — correct about the one price it speaks to
 * by construction, where only chronology can settle a dispute between two.
 * The two kinds answer "do I outrank this" differently enough that the rule
 * lives on each branch rather than as one shared method here.
 */
public sealed interface BazaarDataOrigin permits BazaarDataOrigin.Snapshot, BazaarDataOrigin.UserPositionEvent {
    /** When this write was confirmed or observed, in epoch milliseconds. */
    long timestamp();

    /**
     * A read of the market at large, whose own reported coverage sets the
     * limits of what it may call absent. {@link #maxDepth} bounds how many
     * levels of one side a single read from this source can report at all;
     * reporting fewer than that proves the side was captured whole — see
     * {@link #isExhaustive}. {@link #graceMs} bounds how far this source's
     * own read can lag the book it describes before its timestamp should
     * be trusted at face value against a record it did not itself produce.
     */
    sealed interface Snapshot extends BazaarDataOrigin permits ApiSnapshot, PageSummary {
        /**
         * The most price levels this source can report for one side of one
         * product in a single read. Reporting strictly fewer than this
         * proves that side was captured in full.
         */
        int maxDepth();

        /**
         * How far this source's own read can lag the book state it
         * describes before it may override a record it did not produce.
         * Zero for a source whose read is immediately authoritative for
         * whatever it reports.
         */
        default long graceMs() {
            return 0;
        }

        /**
         * Returns {@code true} when {@code reportedCount} levels for one side proves
         * this source captured that side in full — nothing beyond what it reported
         * exists right now, by this source's own authority.
         */
        default boolean isExhaustive(int reportedCount) {
            return reportedCount < maxDepth();
        }

        /**
         * Returns {@code true} when this read has standing to treat
         * {@code existing} as stale enough to override.
         *
         * <p>A read can never outrank a write stamped strictly after it —
         * chronology settles that regardless of source type. Past that
         * gate, a source with no lag of its own ({@link #graceMs} zero)
         * always has standing. A source that does lag still has
         * unconditional standing against an {@code existing} write from a
         * {@link PageSummary} — one product-page render superseding
         * another is an ordinary refresh, not a question of catching up to
         * a different source's own latency. Against anything else, a
         * lagging source only gains standing once its own
         * {@link #graceMs} has genuinely elapsed since {@code existing}
         * was written; before that, the disagreement may just be this
         * source's own request-and-render delay, not new information.
         *
         * <p>The peer check names {@link PageSummary} directly rather than
         * comparing runtime types, since it is the only lagging source
         * today. A second lagging {@link Snapshot} would need this
         * generalized — two different lag mechanisms should not get a free
         * pass against each other merely for both being nonzero.
         */
        default boolean outranks(BazaarDataOrigin existing) {
            if (this.timestamp() < existing.timestamp()) return false;
            if (this.graceMs() == 0) return true;
            if (existing instanceof PageSummary) return true;

            return this.timestamp() - existing.timestamp() >= this.graceMs();
        }
    }

    /**
     * A full-depth poll of the Hypixel Bazaar HTTP endpoint, covering up
     * to {@value #MAX_DEPTH} levels per side per product.
     */
    record ApiSnapshot(long snapshotTs) implements Snapshot {
        public long timestamp() {
            return snapshotTs;
        }

        /** Maximum bid or ask levels the Hypixel API returns per side per product. */
        public static final int MAX_DEPTH = 30;

        public int maxDepth() {
            return MAX_DEPTH;
        }
    }

    /**
     * The top {@value #MAX_DEPTH} price levels per side, as rendered on
     * the in-game product page.
     *
     * <p>Hypixel serves this page from a short-lived server-side cache, so
     * {@code observedAt} marks when the render was seen, not necessarily
     * how current the data behind it is — the reason {@link #graceMs} is
     * nonzero here.
     */
    record PageSummary(long observedAt) implements Snapshot {
        public long timestamp() {
            return observedAt;
        }

        /** Upper bound on how far a product-page render can lag the true book state. */
        public static final long CACHE_GRACE_MS = 2_500L;

        public long graceMs() {
            return CACHE_GRACE_MS;
        }

        /** Price levels rendered per side on the product page. */
        public static final int MAX_DEPTH = 7;

        public int maxDepth() {
            return MAX_DEPTH;
        }
    }

    /**
     * A single confirmed fact about the player's own Bazaar activity — one
     * point mutation against one price level, or a reconciliation of the
     * player's own tracked positions. Never asserts that a price is absent
     * from the book at large; that authority belongs to {@link Snapshot} alone.
     */
    sealed interface UserPositionEvent extends BazaarDataOrigin
            permits OrderPlaced, OrderFilled, OrderCancelled, OrderExpired, OrderFlipped,
            OrderClaim, InstantDeal, OrdersScreen {

        /**
         * Returns {@code true} when this event is strictly newer than
         * {@code existing}, so an out-of-order replay of a stale player
         * action can never roll back state a more recent one already
         * produced.
         */
        default boolean outranks(BazaarDataOrigin existing) {
            return this.timestamp() > existing.timestamp();
        }
    }

    /** The player placed a new order. Increments the level at its price. */
    record OrderPlaced(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * The player's order finished filling. Decrements whatever volume at
     * that level the book had not yet accounted for.
     */
    record OrderFilled(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** The player cancelled an order. Decrements the unfilled volume it was holding. */
    record OrderCancelled(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * An order lapsed its Hypixel lifetime unfilled — detected either by a
     * periodic check against the order's own expiry stamp, or by observing
     * the expired lore token on the Orders screen. {@link #timestamp()} is
     * when this was detected, not necessarily the exact instant the order
     * expired.
     */
    record OrderExpired(long timestamp) implements UserPositionEvent { }

    /**
     * The player flipped a filled buy order into a new sell offer. Places
     * the new sell level; the claimed buy side needs no mutation of its
     * own, since its volume was already decremented when the original fill
     * was recorded.
     */
    record OrderFlipped(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * The player claimed filled items or coins. Never mutates the book —
     * that volume was already decremented when the fill itself was
     * recorded. Exists so a claim still carries its own timestamp and
     * description for logging.
     */
    record OrderClaim(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /** The player bought or sold instantly against standing orders. Decrements whatever volume the deal consumed. */
    record InstantDeal(long confirmedAt) implements UserPositionEvent {
        public long timestamp() { return confirmedAt; }
    }

    /**
     * One reconciliation pass over the player's own Orders page.
     * Authoritative for the fill progress, claim progress, and existence
     * of the player's own tracked orders as observed at render time;
     * asserts nothing about the book beyond the decrements those
     * observations themselves imply.
     */
    record OrdersScreen(long observedAt) implements UserPositionEvent {
        public long timestamp() { return observedAt; }
    }

    /** Returns a short, human-readable description of this write, used in debug and player-facing log lines. */
    default String describe() {
        return switch (this) {
            case ApiSnapshot snapshot -> "API snapshot @ " + snapshot.snapshotTs();
            case PageSummary summary -> "Item Summary @ " + summary.observedAt();
            case OrdersScreen screen -> "Orders screen @ " + screen.observedAt();
            case OrderPlaced placement -> "Order placed @ " + placement.confirmedAt();
            case OrderFilled filling -> "Order filled @ " + filling.confirmedAt();
            case OrderCancelled cancelment -> "Order cancelled @ " + cancelment.confirmedAt();
            case OrderExpired expiry -> "Order expired @ " + expiry.timestamp();
            case OrderFlipped flip -> "Order flipped @ " + flip.confirmedAt();
            case OrderClaim claim -> "Order claimed @ " + claim.confirmedAt();
            case InstantDeal action -> "Instant deal @ " + action.confirmedAt();
        };
    }
}