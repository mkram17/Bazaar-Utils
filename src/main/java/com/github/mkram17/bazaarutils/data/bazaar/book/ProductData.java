package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * A product's order book: ask and bid price levels, each held as two
 * independently written layers reconciled together only at read time.
 *
 * <p>{@code base} is fed exclusively by market-wide reads —
 * {@link BazaarDataOrigin.Snapshot} polls — and replaced wholesale, price by
 * price, as fresher ones arrive. {@code overlay} is fed exclusively by the
 * player's own confirmed activity — placing, filling, cancelling, and the
 * floors screen reconciliation asserts — and is never overwritten by a
 * market read directly. Every read merges the two through
 * {@link LevelReconciliation#prevailing}, which in turn defers to
 * {@link BazaarDataOrigin.Snapshot#outranks} to decide, for a given price,
 * whose account currently holds.
 *
 * <p>An earlier design kept both kinds of write in one shared map and
 * arbitrated only at the moment a write collided with an existing key. An
 * insert at an absent key never collides with anything, so a market read
 * landing after the player's own fill or cancel could walk straight back
 * into a price the player had just proven empty. Splitting the layers
 * removes that failure outright — nothing here ever commits a merged
 * answer, so no write's correctness depends on knowing what the other layer
 * currently holds.
 *
 * <p>{@link #book} and {@link #entryAt} are the structural reads: the full
 * {@link LevelReconciliation} at a price, including entries overlay is
 * holding purely to confirm a price is empty. {@link #tradableLevels}
 * reduces that down to what almost every caller actually wants — plain
 * {@link PriceLevel}s, kept only where real volume remains.
 *
 * <p>Orientation: asks ascending, so {@code firstEntry()} is the lowest
 * ask; bids descending, so {@code firstEntry()} is the highest bid.
 */
public final class ProductData implements ProductInfo {
    @Getter
    @NotNull
    private final String productId;

    // Fed only by market reads — every entry here carries a Snapshot origin,
    // since apply(Snapshot) is the only method that ever writes to these maps.
    private final NavigableMap<Double, PriceLevel> baseAsks = new TreeMap<>();
    private final NavigableMap<Double, PriceLevel> baseBids = new TreeMap<>(Comparator.reverseOrder());

    // Fed only by the player's own confirmed activity — every entry here
    // carries a UserPositionEvent origin, since place/decrement/walk and the
    // UserPositionEvent overload of apply are the only writers. An entry
    // decremented to zero volume is kept rather than removed: it is the
    // player's own confirmed record that this price is empty, and it has to
    // remain comparable against a later, staler market read — see
    // LevelReconciliation#isVacated.
    private final NavigableMap<Double, PriceLevel> overlayAsks = new TreeMap<>();
    private final NavigableMap<Double, PriceLevel> overlayBids = new TreeMap<>(Comparator.reverseOrder());

    private volatile long lastApiUpdateTs = -1;

    /** Returns {@code true} once at least one snapshot has been applied to this product this session. */
    public boolean hasReceivedSnapshot() {
        return lastApiUpdateTs > 0;
    }

    /**
     * Records {@code timestamp} as the most recently applied snapshot.
     * Callers stamp this only after the snapshot's levels have actually been
     * applied, so {@link #hasReceivedSnapshot} still answers {@code false}
     * while that very first snapshot is being processed.
     */
    public void markSnapshot(long timestamp) {
        this.lastApiUpdateTs = timestamp;
    }

    public ProductData(@NotNull String productId) {
        this.productId = productId;
    }

    /** Returns the base map for {@code type}. */
    private @NotNull NavigableMap<Double, PriceLevel> baseFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? baseAsks : baseBids;
    }

    /** Returns the overlay map for {@code type}. */
    private @NotNull NavigableMap<Double, PriceLevel> overlayFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? overlayAsks : overlayBids;
    }

    /**
     * Returns every price either layer holds an opinion about on this side,
     * reconciled — vacated entries included. {@link #tradableLevels} is this
     * same computation, reduced to plain, liquid levels; use this instead
     * when a caller needs to know whether the two layers currently agree.
     *
     * <p>Computed fresh on every call and unmodifiable.
     */
    public @NotNull NavigableMap<Double, LevelReconciliation> book(@NotNull PriceType type) {
        var base = baseFor(type);
        var overlay = overlayFor(type);

        NavigableMap<Double, LevelReconciliation> result = new TreeMap<>(base.comparator());
        var prices = new HashSet<>(base.keySet());
        prices.addAll(overlay.keySet());

        for (double price : prices) {
            result.put(price, new LevelReconciliation(price, base.get(price), overlay.get(price)));
        }

        return Collections.unmodifiableNavigableMap(result);
    }

    /** @see #book(PriceType) */
    public @NotNull NavigableMap<Double, LevelReconciliation> book(@NotNull TransactionType transaction) {
        return book(transaction.getPriceType());
    }

    /** The single-price form of {@link #book} — the reconciliation at exactly {@code price}, if either layer has one. */
    public @NotNull Optional<LevelReconciliation> entryAt(@NotNull PriceType type, double price) {
        PriceLevel base = baseFor(type).get(price);
        PriceLevel overlay = overlayFor(type).get(price);

        if (base == null && overlay == null) return Optional.empty();

        return Optional.of(new LevelReconciliation(price, base, overlay));
    }

    /** @see #entryAt(PriceType, double) */
    public @NotNull Optional<LevelReconciliation> entryAt(@NotNull TransactionType transaction, double price) {
        return entryAt(transaction.getPriceType(), price);
    }

    /**
     * Returns {@link #book}'s prevailing level at every price, kept only
     * where it still carries real volume.
     */
    public @NotNull NavigableMap<Double, PriceLevel> tradableLevels(@NotNull PriceType type) {
        NavigableMap<Double, PriceLevel> result = new TreeMap<>(baseFor(type).comparator());

        for (var entry : book(type).values()) {
            entry.tradable().ifPresent(level -> result.put(entry.pricePerUnit(), level));
        }

        return Collections.unmodifiableNavigableMap(result);
    }

    /** @see #tradableLevels(PriceType) */
    public @NotNull NavigableMap<Double, PriceLevel> tradableLevels(@NotNull TransactionType transaction) {
        return tradableLevels(transaction.getPriceType());
    }

    /**
     * The write-path seed for {@code price}: {@link #entryAt}'s prevailing
     * value with the {@code Optional} and lineage stripped, or {@code null}
     * if neither layer has one. Kept separate from {@link #entryAt} purely
     * to avoid building a {@link LevelReconciliation} on every
     * {@link #place}/{@link #decrement} call — the arbitration itself is not
     * duplicated, only the plumbing around it.
     */
    private @Nullable PriceLevel seedAt(@NotNull PriceType type, double price) {
        return LevelReconciliation.prevailing(baseFor(type).get(price), overlayFor(type).get(price));
    }

    /**
     * Returns how many tradable levels sit strictly ahead of {@code price}
     * on this side. Zero means {@code price} is currently the best
     * available; a positive count is how many distinct better prices are
     * already occupied.
     */
    public int positionOf(@NotNull PriceType type, double price) {
        return tradableLevels(type).headMap(price).size();
    }

    /** @see #positionOf(PriceType, double) */
    public int positionOf(@NotNull TransactionType transaction, double price) {
        return tradableLevels(transaction).headMap(price).size();
    }

    /**
     * Returns {@code true} when {@code price} falls within what this side
     * has actually observed — at or better than the worst tradable level
     * currently stored. An empty side has observed nothing and always
     * answers {@code false}.
     *
     * <p>Needs no separate case for a side captured in full: when fewer
     * levels are stored than a source's own
     * {@link BazaarDataOrigin.Snapshot#maxDepth} allows, the worst stored
     * price already is the true worst price in the market, so the plain
     * comparison answers correctly without help.
     */
    public boolean isPriceWithinCoverage(@NotNull PriceType type, double price) {
        var worst = tradableLevels(type).lastEntry();

        return worst != null && type.atLeastAsGood(price, worst.getKey());
    }

    /** @see #isPriceWithinCoverage(PriceType, double) */
    public boolean isPriceWithinCoverage(@NotNull TransactionType transaction, double price) {
        return isPriceWithinCoverage(transaction.getPriceType(), price);
    }

    /**
     * Splices a market read into base, then removes whatever it can prove no
     * longer exists — from base outright, and from overlay too, wherever
     * this read's own authority reaches that far.
     *
     * <h3>1. Supersession</h3>
     * Each incoming level competes only against whatever base already holds
     * at that price, through {@link PriceLevel#isSupersededBy}. Overlay
     * plays no part in this step.
     *
     * <h3>2. Absence</h3>
     * A stored price {@code incoming} left unmentioned is removed when this
     * read has grounds to call it gone: unconditionally, if {@code origin}
     * reported enough levels to prove the whole side was captured
     * ({@link BazaarDataOrigin.Snapshot#isExhaustive}); otherwise only
     * within the price range {@code incoming} actually covered, since a
     * partial read cannot speak for prices outside what it looked at.
     * Wherever that authority does reach, both layers are checked — a proof
     * of absence strong enough to evict anything is a fact about the market
     * itself, not about which layer happens to be sitting on a now-wrong
     * record of it.
     *
     * <h3>3. Housekeeping</h3>
     * Once a price has a fresh base value, any overlay entry still sitting
     * there is discarded if {@link LevelReconciliation#prevailing} would now
     * settle on base anyway. No read's answer changes either way — an
     * already-beaten overlay entry never prevailed — this step only clears
     * out what nothing needs kept around for.
     *
     * @return {@code true} if any level was added, replaced, or evicted in either layer.
     */
    public boolean apply(
            @NotNull PriceType type,
            @NotNull List<PriceLevel> incoming,
            @NotNull BazaarDataOrigin.Snapshot origin) {
        var base = baseFor(type);
        var overlay = overlayFor(type);
        var incomingPrices = new HashSet<Double>(incoming.size());

        boolean changed = false;

        // 1. Supersession — base only; overlay is untouched here.
        for (PriceLevel next : incoming) {
            double price = next.pricePerUnit();
            incomingPrices.add(price);

            PriceLevel result = base.merge(price, next, (curr, inc) -> curr.isSupersededBy(inc) ? inc : curr);
            if (result == next) changed = true;
        }

        // 2. Absence — evict wherever this read's authority reaches, in both layers.
        boolean exhaustive = origin.isExhaustive(incomingPrices.size());
        Double lo = incomingPrices.isEmpty() ? null : Collections.min(incomingPrices);
        Double hi = incomingPrices.isEmpty() ? null : Collections.max(incomingPrices);

        var candidates = new HashSet<>(base.keySet());
        candidates.addAll(overlay.keySet());
        candidates.removeAll(incomingPrices);

        // Skip anything outside this read's authority: a partial read proves
        // nothing beyond the range it actually observed.
        for (double price : candidates) {
            if (!exhaustive && (lo == null || price < lo || price > hi)) continue;

            PriceLevel baseLevel = base.get(price);
            if (baseLevel != null && origin.outranks(baseLevel.origin())) {
                base.remove(price);
                changed = true;
            }

            PriceLevel overlayLevel = overlay.get(price);
            if (overlayLevel != null && origin.outranks(overlayLevel.origin())) {
                overlay.remove(price);
                changed = true;
            }
        }

        // 3. Housekeeping — drop overlay entries this read's fresh base value now permanently beats.
        for (double price : incomingPrices) {
            PriceLevel baseLevel = base.get(price);
            PriceLevel overlayLevel = overlay.get(price);

            if (baseLevel != null && overlayLevel != null && LevelReconciliation.prevailing(baseLevel, overlayLevel) == baseLevel) {
                overlay.remove(price);
            }
        }

        return changed;
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.Snapshot) */
    public boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel> incoming, @NotNull BazaarDataOrigin.Snapshot origin) {
        return apply(transaction.getPriceType(), incoming, origin);
    }

    /**
     * Raises overlay's floor at each of {@code incoming}'s prices to at
     * least the given volume. Never lowers a level, and never touches base.
     *
     * <p>Used by Orders-screen reconciliation to assert a floor for orders
     * whose local write history is incomplete this session — an order
     * restored from persisted storage, for instance, never went through
     * {@link #place} this session, so overlay has no organic record of its
     * contribution until something asserts one on its behalf.
     *
     * <p>Compares against {@link #seedAt}, so nothing is raised above what
     * base already, correctly, shows. Ordering, though, is judged only
     * against overlay's own prior entry — the same peer comparison
     * {@link #place} and {@link #decrement} use — since the seed may
     * currently be base-sourced and would be the wrong basis for deciding
     * whether this is a stale replay of the player's own history.
     */
    public boolean apply(
            @NotNull PriceType type,
            @NotNull List<PriceLevel> incoming,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var overlay = overlayFor(type);
        boolean changed = false;

        for (var floor : incoming) {
            double price = floor.pricePerUnit();

            PriceLevel priorOverlay = overlay.get(price);
            if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) continue;

            PriceLevel current = seedAt(type, price);
            if (current != null && current.totalVolume() >= floor.totalVolume()) continue;

            int orderCount = current == null ? floor.orderCount() : Math.max(current.orderCount(), floor.orderCount());
            overlay.put(price, new PriceLevel(price, floor.totalVolume(), orderCount, origin));
            changed = true;
        }

        return changed;
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.UserPositionEvent) */
    public boolean apply(
            @NotNull TransactionType transaction,
            @NotNull List<PriceLevel> incoming,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return apply(transaction.getPriceType(), incoming, origin);
    }

    /**
     * Optimistically increments — or creates — a level in overlay when the
     * player places an order.
     *
     * <p>Seeds from {@link #seedAt}, so placing into a price where base
     * already carries liquidity correctly produces "base's last known total
     * plus this confirmed addition." Ordering is judged only against
     * overlay's own prior entry at this price, if any.
     *
     * @return {@code true} if the book was mutated.
     */
    public boolean place(
            @NotNull PriceType type,
            double price,
            int amount,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var overlay = overlayFor(type);

        PriceLevel priorOverlay = overlay.get(price);
        if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

        PriceLevel seed = seedAt(type, price);
        overlay.put(price, seed != null
                ? seed.withPlacementIncrement(amount, origin.timestamp())
                : new PriceLevel(price, amount, 1, origin));

        return true;
    }

    /** @see #place(PriceType, double, int, BazaarDataOrigin.UserPositionEvent) */
    public boolean place(@NotNull TransactionType transaction, double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return place(transaction.getPriceType(), price, amount, origin);
    }

    /**
     * Decrements volume at a price in overlay, seeded from {@link #seedAt}
     * the same way {@link #place} is. Restricted to
     * {@link BazaarDataOrigin.UserPositionEvent} sources — only the
     * player's own fills, cancels, instant deals, and screen reconciliation
     * call this.
     *
     * <p>A decrement that reaches zero volume stays in overlay rather than
     * being removed — the confirmed record that this price is now empty,
     * which must remain comparable against a later, staler market read; see
     * {@link LevelReconciliation#isVacated}.
     *
     * @return {@code true} if the level existed, in either layer, and was mutated.
     */
    public boolean decrement(
            @NotNull PriceType type,
            double price,
            long amount,
            boolean terminal,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        PriceLevel seed = seedAt(type, price);
        if (seed == null) return false;

        var overlay = overlayFor(type);
        PriceLevel priorOverlay = overlay.get(price);
        if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

        overlay.put(price, seed.withVolumeDecrement(amount, terminal, origin));
        return true;
    }

    /** @see #decrement(PriceType, double, long, boolean, BazaarDataOrigin.UserPositionEvent) */
    public boolean decrement(@NotNull TransactionType transaction, double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return decrement(transaction.getPriceType(), price, amount, terminal, origin);
    }

    /**
     * Walks this side's tradable book in price order, decrementing each
     * level {@code op} admits by however much of its remaining budget it can
     * take.
     *
     * @return {@code true} if any level was mutated.
     */
    public boolean walk(
            @NotNull PriceType type,
            @NotNull WalkOp op,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = tradableLevels(type);

        long remaining = op.budget();
        boolean changed = false;

        for (var key : new ArrayList<>(book.keySet())) {
            if (remaining <= 0) break;
            var level = book.get(key);
            if (level == null) continue;
            if (!op.admits().test(level, type)) break;

            long taking = Math.min(remaining, level.totalVolume());
            boolean terminal = taking == level.totalVolume() && op.fullDrainIsTerminal();

            changed |= decrement(type, key, taking, terminal, origin);
            remaining -= taking;
        }

        return changed;
    }

    /** @see #walk(PriceType, WalkOp, BazaarDataOrigin.UserPositionEvent) */
    public boolean walk(@NotNull TransactionType transaction, @NotNull WalkOp op, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return walk(transaction.getPriceType(), op, origin);
    }

    /**
     * Returns how much of {@code volume} would be immediately matched at
     * tradable levels no worse than {@code priceLimit}, without mutating
     * anything.
     */
    public long estimateCrossVolume(@NotNull PriceType type, double priceLimit, long volume) {
        var book = tradableLevels(type);
        long remaining = volume;

        for (var entry : book.entrySet()) {
            if (remaining <= 0) break;
            if (entry.getValue().exceedsBoundary(type, priceLimit)) break;
            remaining -= Math.min(remaining, entry.getValue().totalVolume());
        }

        return volume - remaining;
    }

    /**
     * One walk's policy: which levels it may take from, how much it may
     * take in total, and whether fully draining a level also proves the
     * order occupying it is gone.
     *
     * <p>Only {@code admits} varies meaningfully between calls;
     * {@code budget} and {@code fullDrainIsTerminal} are fixed for the whole
     * walk. {@code admits} is left as an ordinary {@link BiPredicate}
     * rather than a bespoke interface so combining conditions later, if ever
     * needed, is just {@code .and(...)}.
     *
     * @param admits              gates whether the walk may take from a
     *                            level at all; checked before every level,
     *                            including the first, and the walk stops the
     *                            instant this returns {@code false}.
     * @param budget              total volume the walk may take across all
     *                            admitted levels; {@link Long#MAX_VALUE} for
     *                            no cap.
     * @param fullDrainIsTerminal whether exactly exhausting a level's volume
     *                            also proves its order count should drop.
     *                            Only {@link #ahead} sets this {@code true}:
     *                            an ordinary volume walk consumes a known
     *                            amount from a level that may still hold
     *                            several distinct orders, so draining its
     *                            displayed volume doesn't prove they are all
     *                            individually gone — {@link #ahead}'s
     *                            eviction is a direct claim about the whole
     *                            level, not merely about volume.
     */
    public record WalkOp(@NotNull BiPredicate<PriceLevel, PriceType> admits, long budget, boolean fullDrainIsTerminal) {

        /** Unbounded consumption at any price, up to {@code volume} total — the instant-deal case. */
        public static WalkOp upTo(long volume) {
            return new WalkOp((level, type) -> true, volume, false);
        }

        /** Consumption up to {@code volume}, never past a level worse than {@code priceLimit} — the order-placement crossing case. */
        public static WalkOp upTo(long volume, double priceLimit) {
            return new WalkOp((level, type) -> !level.exceedsBoundary(type, priceLimit), volume, false);
        }

        /**
         * Full, unbounded drain of every level strictly better-priced than
         * {@code price} — see
         * {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation#evictAhead}
         * for why a chat-confirmed fill has standing to make that claim even
         * though it arrives as a {@link BazaarDataOrigin.UserPositionEvent}.
         */
        public static WalkOp ahead(double price) {
            return new WalkOp((level, type) -> type.strictlyBetter(level.pricePerUnit(), price), Long.MAX_VALUE, true);
        }
    }
}