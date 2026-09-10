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
     * @return {@code true} if any level was added, replaced, or evicted in either layer.
     */
    public <O extends BazaarDataOrigin.Snapshot> boolean apply(@NotNull PriceType type, @NotNull List<PriceLevel<O>> incoming, @NotNull O origin, @NotNull AbsenceScope.Window coverage) {
        return sideFor(type).apply(incoming, origin, coverage);
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.Snapshot, AbsenceScope.Window) */
    public <O extends BazaarDataOrigin.Snapshot> boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel<O>> incoming, @NotNull O origin, @NotNull AbsenceScope.Window coverage) {
        return apply(transaction.getPriceType(), incoming, origin, coverage);
    }

    /** Raises overlay's floor at each of {@code incoming}'s prices to at least the given volume. Never lowers a level, never touches base. */
    public boolean apply(@NotNull PriceType type, @NotNull List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> incoming, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return sideFor(type).apply(incoming, origin);
    }

    /** @see #apply(PriceType, List, BazaarDataOrigin.UserPositionEvent) */
    public boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> incoming, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
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
     * re-derived from {@code type} on every access the way
     * {@code baseFor}/{@code overlayFor} used to: asks ascending, so
     * {@code firstEntry()} is the lowest ask; bids descending, so
     * {@code firstEntry()} is the highest bid.
     */
    private static final class BookSide {
        private final @NotNull PriceType type;

        // Fed only by market reads — every entry here carries a Window origin,
        // since apply(Window) is the only method that ever writes to these maps.
        private final @NotNull NavigableMap<Double, PriceLevel<BazaarDataOrigin.Snapshot>> base;

        // Fed only by the player's own confirmed activity — every entry here
        // carries a UserPositionEvent origin, since place/decrement/walk and the
        // UserPositionEvent overload of apply are the only writers. An entry
        // decremented to zero volume is kept rather than removed: it is the
        // player's own confirmed record that this price is empty, and it has to
        // remain comparable against a later, staler market read — see
        // LevelReconciliation#isVacated.
        private final @NotNull NavigableMap<Double, PriceLevel<BazaarDataOrigin.UserPositionEvent>> overlay;

        /**
         * This side's current derived view: {@link Cold}, holding only
         * whatever single-price answers have been asked for since the last
         * mutation, or {@link Warm}, holding a complete {@link BookCache}.
         * A sealed alternative rather than a nullable {@code BookCache} plus
         * a separately-managed points map, because the two were never
         * simultaneously meaningful — see the class discussion. One field
         * means the transition between states is a single reassignment,
         * with nothing else to remember to clear alongside it.
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

        /** Marks this side dirty. Called only from a path that has already confirmed a real mutation happened. */
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

        /** Builds both derived views in one pass over the unioned price set. */
        private @NotNull BookCache recompute() {
            Set<Double> prices = new TreeSet<>(base.comparator());
            prices.addAll(base.keySet());
            prices.addAll(overlay.keySet());

            var reconciledBuilder = new ImmutableSortedMap.Builder<Double, LevelReconciliation>(base.comparator());
            var tradableBuilder = new ImmutableSortedMap.Builder<Double, PriceLevel<?>>(base.comparator());

            for (double price : prices) {
                var level = new LevelReconciliation(price, base.get(price), overlay.get(price));
                reconciledBuilder.put(price, level);

                var tradable = level.tradableOrNull();
                if (tradable != null) tradableBuilder.put(price, tradable);
            }

            return new BookCache(reconciledBuilder.build(), tradableBuilder.build());
        }

        @NotNull NavigableMap<Double, LevelReconciliation> book() {
            return cacheFor().reconciled();
        }

        @NotNull NavigableMap<Double, PriceLevel<?>> tradableLevels() {
            return cacheFor().tradable();
        }

        @NotNull Optional<LevelReconciliation> entryAt(double price) {
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
         * The write-path seed for {@code price}: {@link #entryAt}'s
         * prevailing value with the {@code Optional} and lineage stripped,
         * or {@code null} if neither layer has one.
         *
         * <p>Reads {@code base}/{@code overlay} directly rather than through
         * {@link #view} — the one read in this class that has to.
         * {@link #place} calls {@link #invalidate}, then this, then mutates
         * overlay, with no invalidation after that write; the ordering is
         * correct only because this method never touches {@link #view}.
         * Route it through {@link #cacheFor} or {@link #entryAt} instead and
         * the rebuild triggered right after {@link #invalidate} would
         * capture the pre-write state, with no invalidation left to fix it.
         */
        private @Nullable PriceLevel<?> seedAt(double price) {
            return LevelReconciliation.prevailing(base.get(price), overlay.get(price));
        }

        /**
         * Returns how many tradable levels sit strictly ahead of {@code price}
         * on this side. Zero means {@code price} is currently the best
         * available; a positive count is how many distinct better prices are
         * already occupied.
         */
        int positionOf(double price) {
            return tradableLevels().headMap(price).size();
        }

        /**
         * Splices a market read into base, then removes whatever it can
         * prove no longer exists — from base outright, and from overlay
         * too, wherever this read's own authority reaches that far.
         *
         * <h3>1. Supersession</h3>
         * Each incoming level competes only against whatever base already
         * holds at that price, through {@link PriceLevel#isSupersededBy}.
         *
         * <h3>2. Absence</h3>
         * A stored price {@code incoming} left unmentioned is removed when
         * this read has grounds to call it gone — {@link AbsenceScope.Window#contains(PriceType, double)}.
         *
         * <h3>3. Housekeeping</h3>
         * Once a price has a fresh base value, any overlay entry still
         * sitting there is discarded if {@link LevelReconciliation#prevailing}
         * would now settle on base anyway.
         *
         * @return {@code true} if any level was added, replaced, or evicted in either layer.
         */
        <O extends BazaarDataOrigin.Snapshot> boolean apply(@NotNull List<PriceLevel<O>> incoming, @NotNull O origin, @NotNull AbsenceScope.Window coverage) {
            var incomingPrices = new HashSet<Double>(incoming.size());
            boolean changed = false;

            // 1. Supersession — base only; overlay is untouched here.
            for (PriceLevel<O> next : incoming) {
                double price = next.pricePerUnit();
                incomingPrices.add(price);

                PriceLevel<BazaarDataOrigin.Snapshot> result = base.merge(price, (PriceLevel<BazaarDataOrigin.Snapshot>) next, (curr, inc) -> PriceLevel.isSupersededBy(curr, inc) ? inc : curr);
                if (result == next) changed = true;
            }

            // 2. Absence — evict wherever this read's authority reaches, in both layers.
            var candidates = new HashSet<>(base.keySet());
            candidates.addAll(overlay.keySet());
            candidates.removeAll(incomingPrices);

            for (double price : candidates) {
                if (!coverage.contains(type, price)) continue;

                var baseLevel = base.get(price);
                if (baseLevel != null && origin.outranks(baseLevel.origin())) {
                    base.remove(price);
                    changed = true;
                }

                var overlayLevel = overlay.get(price);
                if (overlayLevel != null && origin.outranks(overlayLevel.origin())) {
                    overlay.remove(price);
                    changed = true;
                }
            }

            // 3. Housekeeping — drop overlay entries this read's fresh base value now permanently beats.
            for (double price : incomingPrices) {
                var baseLevel = base.get(price);
                var overlayLevel = overlay.get(price);

                if (baseLevel != null && overlayLevel != null && LevelReconciliation.prevailing(baseLevel, overlayLevel) == baseLevel) {
                    overlay.remove(price);
                }
            }

            if (changed) invalidate();

            return changed;
        }

        /**
         * Raises overlay's floor at each of {@code incoming}'s prices to at
         * least the given volume. Compares against {@link #seedAt}, so
         * nothing is raised above what base already, correctly, shows;
         * ordering is judged only against overlay's own prior entry.
         */
        boolean apply(@NotNull List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> incoming, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            boolean changed = false;

            for (var floor : incoming) {
                double price = floor.pricePerUnit();

                var priorOverlay = overlay.get(price);
                if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) continue;

                var current = seedAt(price);
                if (current != null && current.totalVolume() >= floor.totalVolume()) continue;

                int orderCount = current == null ? floor.orderCount() : Math.max(current.orderCount(), floor.orderCount());
                overlay.put(price, new PriceLevel<>(price, floor.totalVolume(), orderCount, origin));
                changed = true;
            }

            if (changed) invalidate();

            return changed;
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
        boolean place(double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            var priorOverlay = overlay.get(price);
            if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

            invalidate();

            var seed = seedAt(price);
            overlay.put(price, seed != null
                    ? seed.withPlacementIncrement(amount, origin)
                    : new PriceLevel<>(price, amount, 1, origin));

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
         * @return {@code true} if the level existed, in either layer, and was mutated.
         */
        boolean decrement(double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            var seed = seedAt(price);
            if (seed == null) return false;

            var priorOverlay = overlay.get(price);
            if (priorOverlay != null && !origin.outranks(priorOverlay.origin())) return false;

            invalidate();
            overlay.put(price, seed.withVolumeDecrement(amount, terminal, origin));

            return true;
        }

        /**
         * Walks this side's tradable book in price order, decrementing each
         * level {@code op} admits by however much of its remaining budget it can
         * take.
         *
         * @return {@code true} if any level was mutated.
         */
        boolean walk(@NotNull WalkOp op, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
            var book = tradableLevels();
            long remaining = op.budget();
            boolean changed = false;

            for (var key : new ArrayList<>(book.keySet())) {
                if (remaining <= 0) break;
                var level = book.get(key);
                if (level == null) continue;
                if (!op.admits().test(level, type)) break;

                long taking = Math.min(remaining, level.totalVolume());
                boolean terminal = taking == level.totalVolume() && op.terminalScope() != null;

                changed |= decrement(key, taking, terminal, origin);
                remaining -= taking;
            }

            return changed;
        }


        /**
         * Returns how much of {@code volume} would be immediately matched at
         * tradable levels no worse than {@code priceLimit}, without mutating
         * anything.
         */
        long estimateCrossVolume(double priceLimit, long volume) {
            var book = tradableLevels();
            long remaining = volume;

            for (var entry : book.entrySet()) {
                if (remaining <= 0) break;
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
     * <p>Only {@code admits} varies meaningfully between calls;
     * {@code budget} and {@code terminalScope} are fixed for the whole
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
     * @param terminalScope       non-null only when exactly exhausting a level's
     *                            volume also proves its order count should drop —
     *                            and, when non-null, the {@link AbsenceScope} that
     *                            claim is authorized to make. Only {@link #ahead}
     *                            sets one: an ordinary volume walk consumes a known
     *                            amount from a level that may still hold several
     *                            distinct orders, so draining its displayed volume
     *                            doesn't prove they are all individually gone —
     *                            {@link #ahead}'s eviction is a direct claim about
     *                            the whole level, not merely about volume.
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