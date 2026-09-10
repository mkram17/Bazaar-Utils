package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.google.common.collect.ImmutableSortedMap;
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
 * <p>{@link #book} and {@link #entryAt} are the structural reads: the full
 * {@link LevelReconciliation} at a price, including entries overlay is
 * holding purely to confirm a price is empty. {@link #tradableLevels}
 * reduces that down to what almost every caller actually wants — plain
 * {@link PriceLevel}s, kept only where real volume remains.
 *
 * <p>Every one of these operations acts on exactly one side at a time, so
 * this class holds two {@link BookSide}s — one per {@link PriceType} — and does
 * nothing itself beyond picking which one a call is about, in {@link #sideFor}.
 *
 * <p>Orientation: asks ascending, so {@code firstEntry()} is the lowest
 * ask; bids descending, so {@code firstEntry()} is the highest bid.
 */
public final class ProductData implements ProductInfo {
    @Getter
    @NotNull
    private final String productId;

    private final BookSide asks = new BookSide(PriceType.INSTABUY);
    private final BookSide bids = new BookSide(PriceType.INSTASELL);

    public ProductData(@NotNull String productId) {
        this.productId = productId;
    }

    /** The one and only place a {@link PriceType} gets turned into a side. */
    private @NotNull ProductData.BookSide sideFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? asks : bids;
    }

    /**
     * Returns every price either layer holds an opinion about on this side,
     * reconciled — vacated entries included. {@link #tradableLevels} is this
     * same computation, reduced to plain, liquid levels; use this instead
     * when a caller needs to know whether the two layers currently agree.
     */
    public @NotNull NavigableMap<Double, LevelReconciliation> book(@NotNull PriceType type) {
        return sideFor(type).book();
    }

    /** @see #book(PriceType) */
    public @NotNull NavigableMap<Double, LevelReconciliation> book(@NotNull TransactionType transaction) {
        return book(transaction.getPriceType());
    }

    /** The single-price form of {@link #book} — the reconciliation at exactly {@code price}, if either layer has one. */
    public @NotNull Optional<LevelReconciliation> entryAt(@NotNull PriceType type, double price) {
        return sideFor(type).entryAt(price);
    }

    /** @see #entryAt(PriceType, double) */
    public @NotNull Optional<LevelReconciliation> entryAt(@NotNull TransactionType transaction, double price) {
        return entryAt(transaction.getPriceType(), price);
    }

    /** Returns {@link #book}'s prevailing level at every price, kept only where it still carries real volume. */
    public @NotNull NavigableMap<Double, PriceLevel<?>> tradableLevels(@NotNull PriceType type) {
        return sideFor(type).tradableLevels();
    }

    /** @see #tradableLevels(PriceType) */
    public @NotNull NavigableMap<Double, PriceLevel<?>> tradableLevels(@NotNull TransactionType transaction) {
        return tradableLevels(transaction.getPriceType());
    }

    /**
     * Returns how many tradable levels sit strictly ahead of {@code price}
     * on this side. Zero means {@code price} is currently the best
     * available; a positive count is how many distinct better prices are
     * already occupied.
     */
    public int positionOf(@NotNull PriceType type, double price) {
        return sideFor(type).positionOf(price);
    }

    /** @see #positionOf(PriceType, double) */
    public int positionOf(@NotNull TransactionType transaction, double price) {
        return positionOf(transaction.getPriceType(), price);
    }

    /**
     * Splices a market read into base, then removes whatever it can prove no
     * longer exists — from base outright, and from overlay too, wherever
     * this read's own authority reaches that far. See
     * {@link BookSide#apply(List, BazaarDataOrigin.Snapshot, AbsenceScope.Window)} for the algorithm.
     *
     * @return {@code true} if the visible tradable book state changed at any price.
     */
    public <O extends BazaarDataOrigin.Snapshot> boolean apply(@NotNull PriceType type, @NotNull List<PriceLevel<O>> incoming, @NotNull O origin, @NotNull AbsenceScope.Window coverage) {
        return sideFor(type).apply(incoming, origin, coverage);
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.Snapshot, AbsenceScope.Window) */
    public <O extends BazaarDataOrigin.Snapshot> boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel<O>> incoming, @NotNull O origin, @NotNull AbsenceScope.Window coverage) {
        return apply(transaction.getPriceType(), incoming, origin, coverage);
    }

    /** Raises overlay's floor at each of {@code incoming}'s prices to at least the given volume and order count. Never lowers a level, never touches base. */
    public boolean apply(@NotNull PriceType type, @NotNull List<? extends PriceLevel<? extends BazaarDataOrigin.UserPositionEvent>> incoming, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return sideFor(type).apply(incoming, origin);
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.UserPositionEvent) */
    public boolean apply(@NotNull TransactionType transaction, @NotNull List<? extends PriceLevel<? extends BazaarDataOrigin.UserPositionEvent>> incoming, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return apply(transaction.getPriceType(), incoming, origin);
    }

    /** Optimistically increments — or creates — a level in overlay when the player places an order. */
    public boolean place(@NotNull PriceType type, double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return sideFor(type).place(price, amount, origin);
    }

    /** @see #place(PriceType, double, int, BazaarDataOrigin.UserPositionEvent) */
    public boolean place(@NotNull TransactionType transaction, double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return place(transaction.getPriceType(), price, amount, origin);
    }

    /** Decrements volume at a price in overlay. Restricted to {@link BazaarDataOrigin.UserPositionEvent} sources. */
    public boolean decrement(@NotNull PriceType type, double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return sideFor(type).decrement(price, amount, terminal, origin);
    }

    /** @see #decrement(PriceType, double, long, boolean, BazaarDataOrigin.UserPositionEvent) */
    public boolean decrement(@NotNull TransactionType transaction, double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return decrement(transaction.getPriceType(), price, amount, terminal, origin);
    }

    /** Walks this side's tradable book in price order, decrementing each level {@code op} admits. */
    public boolean walk(@NotNull PriceType type, @NotNull WalkOp op, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return sideFor(type).walk(op, origin);
    }

    /** @see #walk(PriceType, WalkOp, BazaarDataOrigin.UserPositionEvent) */
    public boolean walk(@NotNull TransactionType transaction, @NotNull WalkOp op, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return walk(transaction.getPriceType(), op, origin);
    }

    /** Returns how much of {@code volume} would be immediately matched at tradable levels no worse than {@code priceLimit}. */
    public long estimateCrossVolume(@NotNull PriceType type, double priceLimit, long volume) {
        return sideFor(type).estimateCrossVolume(priceLimit, volume);
    }

    /**
     * One side of the book — asks or bids — holding its raw layers and its
     * derived-view cache together, since every read and mutation acts on
     * exactly one side as a unit.
     *
     * <p>Orientation is fixed once here, at construction, rather than
     * re-derived from {@code type} on every access: asks ascending, so
     * {@code firstEntry()} is the lowest ask; bids descending, so
     * {@code firstEntry()} is the highest bid.
     */
    private static final class BookSide {
        private final @NotNull PriceType type;

        // Fed only by market reads — every entry here carries a Snapshot origin,
        // since apply(Snapshot) is the only method that ever writes to these maps.
        private final @NotNull NavigableMap<Double, PriceLevel<? extends BazaarDataOrigin.Snapshot>> base;

        // Fed only by the player's own confirmed activity — every entry here
        // carries a UserPositionEvent origin, since place/decrement/walk and the
        // UserPositionEvent overload of apply are the only writers. An entry
        // decremented to zero volume is kept rather than removed: it is the
        // player's own confirmed record that this price is empty, and it has to
        // remain comparable against a later, staler market read — see
        // LevelReconciliation#isVacated.
        private final @NotNull NavigableMap<Double, PriceLevel<? extends BazaarDataOrigin.UserPositionEvent>> overlay;

        /**
         * This side's current derived view: {@link Cold}, holding only
         * whatever single-price answers have been asked for since the last
         * mutation, or {@link Warm}, holding a complete {@link BookCache}.
         */
        private @NotNull DerivedView view;

        private BookSide(@NotNull PriceType type) {
            this.type = type;

            Comparator<Double> order = (type == PriceType.INSTABUY) ? Comparator.naturalOrder() : Comparator.reverseOrder();
            this.base = new TreeMap<>(order);
            this.overlay = new TreeMap<>(order);
            this.view = new Cold();
        }

        /** One side's cached derived views, always recomputed together. */
        private record BookCache(
                @NotNull ImmutableSortedMap<Double, LevelReconciliation> reconciled,
                @NotNull ImmutableSortedMap<Double, PriceLevel<?>> tradable
        ) {}

        private sealed interface DerivedView permits Warm, Cold {}

        private record Warm(@NotNull BookCache cache) implements DerivedView {}

        /** Holds only what's been asked for since the last invalidation, one price at a time. */
        private static final class Cold implements DerivedView {
            private final Map<Double, LevelReconciliation> points = new HashMap<>();
        }

        /** Marks this side dirty. Called only after raw layers have been mutated. */
        private void invalidate() {
            view = new Cold();
        }

        /** This side's current {@link BookCache}, building it first if {@link #view} is {@link Cold}. */
        private @NotNull BookCache cacheFor() {
            if (view instanceof Warm(BookCache cache)) return cache;

            BookCache fresh = recompute();
            view = new Warm(fresh);

            return fresh;
        }

        /** Builds both derived views in one pass over the unioned price set (with a fast path when overlay is empty). */
        private @NotNull BookCache recompute() {
            var reconciledBuilder = new ImmutableSortedMap.Builder<Double, LevelReconciliation>(base.comparator());
            var tradableBuilder = new ImmutableSortedMap.Builder<Double, PriceLevel<?>>(base.comparator());

            if (overlay.isEmpty()) {
                for (var entry : base.entrySet()) {
                    double price = entry.getKey();
                    var baseLevel = entry.getValue();
                    var level = new LevelReconciliation(price, baseLevel, null);
                    reconciledBuilder.put(price, level);

                    if (baseLevel.totalVolume() > 0) tradableBuilder.put(price, baseLevel);
                }

                return new BookCache(reconciledBuilder.build(), tradableBuilder.build());
            }

            Set<Double> prices = new TreeSet<>(base.comparator());
            prices.addAll(base.keySet());
            prices.addAll(overlay.keySet());

            for (double price : prices) {
                var level = new LevelReconciliation(price, base.get(price), overlay.get(price));
                reconciledBuilder.put(price, level);

                var tradable = level.tradableOrNull();
                if (tradable != null) tradableBuilder.put(price, tradable);
            }

            return new BookCache(reconciledBuilder.build(), tradableBuilder.build());
        }

        synchronized @NotNull NavigableMap<Double, LevelReconciliation> book() {
            return cacheFor().reconciled();
        }

        synchronized @NotNull NavigableMap<Double, PriceLevel<?>> tradableLevels() {
            return cacheFor().tradable();
        }

        synchronized @NotNull Optional<LevelReconciliation> entryAt(double price) {
            return switch (view) {
                case Warm warm -> Optional.ofNullable(warm.cache().reconciled().get(price));
                case Cold cold -> Optional.ofNullable(cold.points.computeIfAbsent(price, this::reconcileAt));
            };
        }

        private @Nullable LevelReconciliation reconcileAt(double price) {
            var b = base.get(price);
            var o = overlay.get(price);

            return (b == null && o == null) ? null : new LevelReconciliation(price, b, o);
        }

        /**
         * The write-path seed for {@code price}: the prevailing level from raw
         * {@code base}/{@code overlay}, or {@code null} if neither layer has one.
         */
        private @Nullable PriceLevel<?> seedAt(double price) {
            return LevelReconciliation.prevailing(base.get(price), overlay.get(price));
        }

        private static boolean differsInTradableState(@Nullable PriceLevel<?> before, @Nullable PriceLevel<?> after) {
            boolean beforeLive = before != null && before.totalVolume() > 0;
            boolean afterLive = after != null && after.totalVolume() > 0;
            if (!beforeLive && !afterLive) return false;
            if (beforeLive != afterLive) return true;

            return !before.agreesWith(after);
        }

        /**
         * Returns how many tradable levels sit strictly ahead of {@code price}
         * on this side. Zero means {@code price} is currently the best
         * available; a positive count is how many distinct better prices are
         * already occupied.
         */
        synchronized int positionOf(double price) {
            return tradableLevels().headMap(price).size();
        }

        /**
         * Splices a market read into base, retires any overlay entries the fresh
         * base value now permanently outranks, and removes whatever unmentioned
         * prices this read's coverage can prove no longer exist.
         *
         * <p>Cache invalidation ({@code viewDirty}) triggers whenever either raw
         * map changes — including origin timestamp refreshes and tombstone GC —
         * while the return value ({@code marketChanged}) is {@code true} only when
         * the externally visible tradable book state actually changed.
         *
         * @return {@code true} if the visible tradable book state changed at any price.
         */
        synchronized <O extends BazaarDataOrigin.Snapshot> boolean apply(
                @NotNull List<PriceLevel<O>> incoming,
                @NotNull O origin,
                @NotNull AbsenceScope.Window coverage) {
            boolean hadBase = !base.isEmpty();
            boolean hadOverlay = !overlay.isEmpty();
            Set<Double> incomingPrices = (hadBase || hadOverlay) ? HashSet.newHashSet(incoming.size()) : null;
            boolean viewDirty = false;
            boolean marketChanged = false;

            // 1. Supersession & inline housekeeping — advance base whenever provenance
            //    improves (even when substance agrees, so stale overlays can eventually retire).
            for (PriceLevel<O> next : incoming) {
                double price = next.pricePerUnit();
                if (incomingPrices != null) incomingPrices.add(price);

                var prevBase = base.get(price);
                if (prevBase == null || PriceLevel.canAdvanceOrigin(prevBase, next)) {
                    var prevOverlay = hadOverlay ? overlay.get(price) : null;
                    var before = LevelReconciliation.prevailing(prevBase, prevOverlay);

                    base.put(price, next);
                    viewDirty = true;

                    var afterOverlay = prevOverlay;
                    if (prevOverlay != null && LevelReconciliation.prevailing(next, prevOverlay) == next) {
                        overlay.remove(price);
                        afterOverlay = null;
                    }

                    var after = LevelReconciliation.prevailing(next, afterOverlay);
                    if (differsInTradableState(before, after)) {
                        marketChanged = true;
                    }
                }
            }

            // 2. Absence — evict unmentioned prices wherever this read's authority reaches.
            if (hadBase) {
                var it = base.entrySet().iterator();
                while (it.hasNext()) {
                    var entry = it.next();
                    double price = entry.getKey();
                    if (incomingPrices.contains(price)) continue;
                    if (!coverage.contains(type, price)) continue;

                    var baseLevel = entry.getValue();
                    if (origin.outranks(baseLevel.origin())) {
                        var prevOverlay = overlay.get(price);
                        var before = LevelReconciliation.prevailing(baseLevel, prevOverlay);
                        it.remove();
                        viewDirty = true;

                        var afterOverlay = prevOverlay;
                        if (prevOverlay != null && origin.outranks(prevOverlay.origin())) {
                            overlay.remove(price);
                            afterOverlay = null;
                        }

                        if (differsInTradableState(before, afterOverlay)) {
                            marketChanged = true;
                        }
                    }
                }
            }

            if (!overlay.isEmpty()) {
                var it = overlay.entrySet().iterator();
                while (it.hasNext()) {
                    var entry = it.next();
                    double price = entry.getKey();
                    if (incomingPrices.contains(price)) continue;
                    if (!coverage.contains(type, price)) continue;

                    var overlayLevel = entry.getValue();
                    if (origin.outranks(overlayLevel.origin())) {
                        var remainingBase = base.get(price);
                        var before = LevelReconciliation.prevailing(remainingBase, overlayLevel);
                        it.remove();
                        viewDirty = true;

                        if (differsInTradableState(before, remainingBase)) {
                            marketChanged = true;
                        }
                    }
                }
            }

            if (viewDirty) invalidate();

            return marketChanged;
        }

        /**
         * Raises overlay's floor at each of {@code incoming}'s prices to at
         * least the given volume and order count. Compares against {@link #seedAt},
         * so nothing is raised above what base already correctly shows;
         * ordering is judged only against overlay's own prior entry.
         */
        synchronized boolean apply(
                @NotNull List<? extends PriceLevel<? extends BazaarDataOrigin.UserPositionEvent>> incoming,
                @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            boolean changed = false;

            for (var floor : incoming) {
                double price = floor.pricePerUnit();

                var priorOverlay = overlay.get(price);
                if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) continue;

                var current = seedAt(price);
                if (current != null
                        && current.totalVolume() >= floor.totalVolume()
                        && current.orderCount() >= floor.orderCount()) {
                    continue;
                }

                long volume = current == null ? floor.totalVolume() : Math.max(current.totalVolume(), floor.totalVolume());
                int orderCount = current == null ? floor.orderCount() : Math.max(current.orderCount(), floor.orderCount());
                overlay.put(price, new PriceLevel<>(price, volume, orderCount, origin));
                changed = true;
            }

            if (changed) invalidate();

            return changed;
        }

        /**
         * Optimistically increments — or creates — a level in overlay when the
         * player places an order.
         *
         * <p>Seeds from {@link #seedAt}, treating a vacated tombstone ({@code totalVolume == 0})
         * as an empty slot so placing into a vacated price produces a fresh single-order level.
         *
         * @return {@code true} if the book was mutated.
         */
        synchronized boolean place(double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            if (amount <= 0) return false;

            var priorOverlay = overlay.get(price);
            if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

            var seed = seedAt(price);
            PriceLevel<BazaarDataOrigin.UserPositionEvent> updated = (seed != null && seed.totalVolume() > 0)
                    ? seed.withPlacementIncrement(amount, origin)
                    : new PriceLevel<>(price, amount, 1, origin);

            overlay.put(price, updated);
            invalidate();

            return true;
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
         * @return {@code true} if a live level existed and was mutated.
         */
        synchronized boolean decrement(double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            if (amount <= 0L) return false;

            var seed = seedAt(price);
            if (seed == null || seed.totalVolume() == 0L) return false;

            var priorOverlay = overlay.get(price);
            if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

            overlay.put(price, seed.withVolumeDecrement(amount, terminal, origin));
            invalidate();

            return true;
        }

        /**
         * Walks this side's tradable book in price order, decrementing each
         * level {@code op} admits by however much of its remaining budget it can
         * take.
         *
         * @return {@code true} if any level was mutated.
         */
        synchronized boolean walk(@NotNull WalkOp op, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            var book = tradableLevels();
            long remaining = op.budget();
            boolean changed = false;

            for (var entry : book.entrySet()) {
                if (remaining <= 0L) break;
                var level = entry.getValue();
                if (!op.admits().test(level, type)) break;

                long taking = Math.min(remaining, level.totalVolume());
                boolean terminal = taking == level.totalVolume() && op.terminalScope() != null;

                changed |= decrement(entry.getKey(), taking, terminal, origin);
                remaining -= taking;
            }

            return changed;
        }

        /**
         * Returns how much of {@code volume} would be immediately matched at
         * tradable levels no worse than {@code priceLimit}, without mutating
         * anything.
         */
        synchronized long estimateCrossVolume(double priceLimit, long volume) {
            if (volume <= 0L) return 0L;

            var book = tradableLevels();
            long remaining = volume;

            for (var entry : book.entrySet()) {
                if (remaining <= 0L) break;
                if (entry.getValue().exceedsBoundary(type, priceLimit)) break;
                remaining -= Math.min(remaining, entry.getValue().totalVolume());
            }

            return volume - remaining;
        }
    }

    /**
     * One walk's policy: which levels it may take from, how much it may
     * take in total, and whether fully draining a level also proves the
     * order occupying it is gone.
     *
     * @param admits        gates whether the walk may take from a level at all;
     *                      checked before every level, including the first, and the
     *                      walk stops the instant this returns {@code false}.
     * @param budget        total volume the walk may take across all admitted levels;
     *                      {@link Long#MAX_VALUE} for no cap.
     * @param terminalScope non-null only when exactly exhausting a level's volume also
     *                      proves its order count should drop — and, when non-null, the
     *                      {@link AbsenceScope} that claim is authorized to make.
     */
    public record WalkOp(
            @NotNull BiPredicate<PriceLevel<?>, PriceType> admits,
            long budget,
            @Nullable AbsenceScope terminalScope) {

        /** Unbounded consumption at any price, up to {@code volume} total — the instant-deal case. */
        public static WalkOp upTo(long volume) {
            return new WalkOp((level, type) -> true, volume, null);
        }

        /** Consumption up to {@code volume}, never past a level worse than {@code priceLimit} — the order-placement crossing case. */
        public static WalkOp upTo(long volume, double priceLimit) {
            return new WalkOp(
                    (level, type) -> !level.exceedsBoundary(type, priceLimit),
                    volume,
                    null
            );
        }

        /**
         * Full, unbounded drain of every level strictly better-priced than
         * {@code price} — see
         * {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation#evictAhead}
         * for why a chat-confirmed fill has standing to make that claim even
         * though it arrives as a {@link BazaarDataOrigin.UserPositionEvent}.
         */
        public static WalkOp ahead(double price) {
            return new WalkOp(
                    (level, type) -> type.strictlyBetter(level.pricePerUnit(), price),
                    Long.MAX_VALUE,
                    new AbsenceScope.BetterThan(price)
            );
        }
    }
}
