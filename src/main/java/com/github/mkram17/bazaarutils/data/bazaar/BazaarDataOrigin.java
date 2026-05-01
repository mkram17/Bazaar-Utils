package com.github.mkram17.bazaarutils.data.bazaar;

/**
 * The source and timestamp behind one write to a product's order book, and,
 * through {@link #outranks(BazaarDataOrigin)}, the standing that write has to
 * override one it disagrees with.
 *
 * <p>Every origin is one of two kinds. A {@link Snapshot} is a read of the
 * market at large — an outside account of the book, right or wrong about any
 * one player's own orders — whose standing depends on how much of the book
 * it actually covered and how far its own read can lag reality. A
 * {@link UserPositionEvent} is the player's own confirmed action — placing,
 * filling, cancelling, claiming — correct about the one price it speaks to
 * by construction, where chronology settles disputes between two writes.
 */
public sealed interface BazaarDataOrigin permits BazaarDataOrigin.Snapshot, BazaarDataOrigin.UserPositionEvent {
    /** When this write was confirmed or observed, in epoch milliseconds. */
    long timestamp();

    /**
     * Returns {@code true} when this write has standing to override or supersede
     * {@code existing}.
     */
    boolean outranks(BazaarDataOrigin existing);

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
         * gate, a source with zero {@link #graceMs} always has standing.
         * Otherwise:
         * <ul>
         *   <li>Against {@link PageSummary}, any non-older snapshot has
         *       unconditional standing (both peer product-page refreshes and
         *       {@link ApiSnapshot} upgrades).</li>
         *   <li>Against {@link ApiSnapshot}, another {@link ApiSnapshot}
         *       refreshes it unconditionally, whereas a {@link PageSummary}
         *       must clear its {@link PageSummary#CACHE_GRACE_MS} window first.</li>
         *   <li>Against a {@link UserPositionEvent}, the snapshot must clear
         *       its own {@link #graceMs()} ({@link ApiSnapshot#FLY_GRACE_MS}
         *       for in-flight HTTP polls, {@link PageSummary#CACHE_GRACE_MS}
         *       for cached product-page renders).</li>
         * </ul>
         */
        @Override
        default boolean outranks(BazaarDataOrigin existing) {
            if (this.timestamp() < existing.timestamp()) return false;
            if (this.graceMs() == 0) return true;

            return switch (existing) {
                case PageSummary ignored -> true;
                case ApiSnapshot ignored -> this instanceof ApiSnapshot
                        || (this.timestamp() - existing.timestamp() >= this.graceMs());
                case UserPositionEvent ignored -> this.timestamp() - existing.timestamp() >= this.graceMs();
            };
        }
    }

    /**
     * A full-depth poll of the Hypixel Bazaar HTTP endpoint, covering up
     * to {@value #MAX_DEPTH} levels per side per product.
     */
    record ApiSnapshot(long snapshotTs) implements Snapshot {
        @Override
        public long timestamp() {
            return snapshotTs;
        }

        /**
         * Grace window protecting recent player actions against in-flight HTTP polls
         * whose server-side capture occurred just before the action settled.
         */
        public static final long FLY_GRACE_MS = 300L;

        @Override
        public long graceMs() {
            return FLY_GRACE_MS;
        }

        /** Maximum bid or ask levels the Hypixel API returns per side per product. */
        public static final int MAX_DEPTH = 30;

        @Override
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
        @Override
        public long timestamp() {
            return observedAt;
        }

        /** Upper bound on how far a product-page render can lag the true book state. */
        public static final long CACHE_GRACE_MS = 2_500L;

        @Override
        public long graceMs() {
            return CACHE_GRACE_MS;
        }

        /** Price levels rendered per side on the product page. */
        public static final int MAX_DEPTH = 7;

        @Override
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
            OrderClaimed, InstantDeal, OrdersScreen {

        /**
         * Returns {@code true} when this event is at least as recent as
         * {@code existing} ({@code >=}), preventing stale out-of-order replays
         * from rolling back newer state while allowing same-millisecond compound
         * mutations and server-ordered actions to stack cleanly.
         */
        @Override
        default boolean outranks(BazaarDataOrigin existing) {
            return this.timestamp() >= existing.timestamp();
        }
    }

    /** The player placed a new order. Increments the level at its price. */
    record OrderPlaced(long confirmedAt) implements UserPositionEvent {
        @Override
        public long timestamp() { return confirmedAt; }
    }

    /**
     * The player's order finished filling. Decrements whatever volume at
     * that level the book had not yet accounted for.
     */
    record OrderFilled(long confirmedAt) implements UserPositionEvent {
        @Override
        public long timestamp() { return confirmedAt; }
    }

    /** The player cancelled an order. Decrements the unfilled volume it was holding. */
    record OrderCancelled(long confirmedAt) implements UserPositionEvent {
        @Override
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
        @Override
        public long timestamp() { return confirmedAt; }
    }

    /**
     * The player claimed filled items or coins. Only mutates the book if
     * the claim reveals previously unobserved fill volume; otherwise carries
     * its timestamp and description for storage transitions and logging.
     */
    record OrderClaimed(long confirmedAt) implements UserPositionEvent {
        @Override
        public long timestamp() { return confirmedAt; }
    }

    /** The player bought or sold instantly against standing orders. Decrements whatever volume the deal consumed. */
    record InstantDeal(long confirmedAt) implements UserPositionEvent {
        @Override
        public long timestamp() { return confirmedAt; }
    }

    /**
     * One reconciliation pass over the player's own Orders page.
     * Authoritative for the fill progress, claim progress, and existence
     * of the player's own tracked orders as observed at render time;
     * asserts nothing about the book beyond the decrements and active-order
     * floors those observations themselves imply.
     */
    record OrdersScreen(long observedAt) implements UserPositionEvent {
        @Override
        public long timestamp() { return observedAt; }
    }

    /** Returns a short, human-readable description of this write, used in debug and player-facing log lines. */
    default String describe() {
        return switch (this) {
            case ApiSnapshot snapshot -> "API snapshot @ " + snapshot.snapshotTs();
            case PageSummary summary -> "Page summary @ " + summary.observedAt();
            case OrdersScreen screen -> "Orders screen @ " + screen.observedAt();
            case OrderPlaced placement -> "Order placed @ " + placement.confirmedAt();
            case OrderFilled filling -> "Order filled @ " + filling.confirmedAt();
            case OrderCancelled cancelment -> "Order cancelled @ " + cancelment.confirmedAt();
            case OrderExpired expiry -> "Order expired @ " + expiry.timestamp();
            case OrderFlipped flip -> "Order flipped @ " + flip.confirmedAt();
            case OrderClaimed claim -> "Order claimed @ " + claim.confirmedAt();
            case InstantDeal action -> "Instant deal @ " + action.confirmedAt();
        };
    }
}
