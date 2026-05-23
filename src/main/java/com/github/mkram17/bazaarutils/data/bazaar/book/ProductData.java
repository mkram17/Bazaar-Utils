package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public final class ProductData implements ProductInfo {
    @NotNull
    private final String productId;

    // sell offers (asks) — natural order,  firstEntry = lowest ask
    // buy orders  (bids) — reverse order,  firstEntry = highest bid
    private final NavigableMap<Double, PriceLevel> asksBook = new TreeMap<>();
    private final NavigableMap<Double, PriceLevel> bidsBook = new TreeMap<>(Comparator.reverseOrder());

    private volatile long lastApiUpdateTs = -1;

    /** True after at least one API snapshot has been processed this session. */
    public boolean hasReceivedSnapshot() {
        return lastApiUpdateTs > 0;
    }

    /**
     * Stamps the latest API snapshot timestamp.
     * Called once per processed {@link ApiSnapshotEvent}, after book application.
     */
    public void markSnapshot(long timestamp) {
        this.lastApiUpdateTs = timestamp;
    }

    /**
     * Maximum number of price levels the Hypixel API returns per side per product.
     * When a snapshot contains fewer than this many levels, it is exhaustive —
     * absence of a price anywhere in the book is authoritative, not just within
     * the observed [lo, hi] window.
     */
    public static final int API_DEPTH = 30;

    public ProductData(@NotNull String productId) {
        this.productId = productId;
    }

    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? asksBook : bidsBook;
    }

    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull TransactionType transaction) {
        return transaction.getPriceType() == PriceType.INSTABUY ? asksBook : bidsBook;
    }

    /**
     * How many price levels are strictly ahead of the given price — 0 means competitive.
     */
    public int positionOf(@NotNull PriceType type, double price) {
        return bookFor(type).headMap(price).size();
    }

    public int positionOf(@NotNull TransactionType transaction, double price) {
        return bookFor(transaction).headMap(price).size();
    }

    /**
     * Merges a market snapshot into the book.
     *
     * <p>Only {@link BazaarDataOrigin.Snapshot} sources may call this.
     * <ol>
     *   <li>Every level present in {@code incoming} is merged using
     *       {@link PriceLevel#isSupersededBy} to decide which wins.</li>
     *   <li>Levels absent from {@code incoming} are evicted according to the
     *       snapshot's authoritative scope (see eviction rules below).</li>
     * </ol>
     *
     * <h3>Eviction authority</h3>
     * <p>A {@link BazaarDataOrigin.PageSummary} is never authoritative for absence —
     * it only covers the levels it explicitly observed and creation is sometimes slow.
     *
     * <p>A {@link BazaarDataOrigin.ApiSnapshot} is authoritative for absence depending
     * on origin of the existing level and depth of the snapshot:
     * <ul>
     *   <li><b>Snapshot-originated levels</b>: always evicted when absent from an
     *       ApiSnapshot. PageSummary evicts only within its observed [lo, hi] price
     *       range.</li>
     *   <li><b>UserPositionEvent-originated levels</b>: evicted by an ApiSnapshot when
     *       the snapshot is exhaustive (fewer than {@link #API_DEPTH} levels returned,
     *       meaning the entire book side was observed). When the snapshot is full-depth
     *       (exactly {@link #API_DEPTH} levels), only levels within the observed [lo, hi]
     *       window are evicted — levels beyond rank 30 are structurally absent from every
     *       snapshot and cannot be inferred as gone from their absence alone.</li>
     * </ul>
     *
     * <p>The timestamp guard applies before all eviction rules: a level stamped at or
     * after the snapshot timestamp is never evicted regardless of origin — the API
     * simply hasn't observed it yet.
     *
     * @return {@code true} if any level was added, replaced, or evicted.
     */
    public boolean apply(
            @NotNull PriceType type,
            @NotNull List<PriceLevel> incoming,
            @NotNull BazaarDataOrigin.Snapshot origin) {
        var book = bookFor(type);
        var incomingPrices = new HashSet<Double>(incoming.size());

        boolean changed = false;

        // 1. Insert / supersede
        for (PriceLevel next : incoming) {
            incomingPrices.add(next.pricePerUnit());

            PriceLevel result = book.merge(next.pricePerUnit(), next, (current, it) -> current.isSupersededBy(it) ? it : current);
            if (result == next) changed = true;
        }

        // 2. Evict absent levels within the origin's authoritative scope.
        //    Timestamp guard runs first — nothing newer than this snapshot is touched.
        changed |= book.entrySet().removeIf(entry -> {
            var level = entry.getValue();
            double key = entry.getKey();

            if (incomingPrices.contains(key)) return false;
            // Never evict a level stamped at or after this snapshot — the API pipeline
            // has a non-zero delay and may not have observed a very recently placed order.
            if (level.origin().timestamp() >= origin.timestamp()) return false;

            return switch (level.origin()) {

                // ── Snapshot-originated levels ────────────────────────────────────
                // ApiSnapshot: always authoritative — evict any stale level it omits.
                // PageSummary: authoritative only within its observed [lo, hi] price
                // range; cannot assert absence of levels outside that window.
                case BazaarDataOrigin.Snapshot ignored -> {
                    if (origin instanceof BazaarDataOrigin.ApiSnapshot) yield true;
                    if (incomingPrices.isEmpty()) yield false;
                    double lo = Collections.min(incomingPrices);
                    double hi = Collections.max(incomingPrices);
                    yield key >= lo && key <= hi;
                }

                // ── UserPositionEvent-originated levels ───────────────────────────
                // These are optimistic writes: order placements, fill decrements,
                // cancel decrements, and screen-reconciliation floor stamps.
                // Only an ApiSnapshot can evict them, and only when it is authoritative
                // for the price in question:
                //
                //   < API_DEPTH levels returned → snapshot is exhaustive for this side;
                //     absence anywhere is authoritative → evict unconditionally.
                //
                //   = API_DEPTH levels returned → snapshot only covers [lo, hi];
                //     a level outside that window is simply beyond rank 30 and was never
                //     observed — its absence proves nothing → evict only within window.
                //
                // The timestamp guard above already protects freshly placed orders that
                // arrived after the snapshot was captured.
                case BazaarDataOrigin.UserPositionEvent ignored -> {
                    if (!(origin instanceof BazaarDataOrigin.ApiSnapshot)) yield false;
                    if (incomingPrices.size() < API_DEPTH) yield true;
                    double lo = Collections.min(incomingPrices);
                    double hi = Collections.max(incomingPrices);
                    yield key >= lo && key <= hi;
                }
            };
        });

        return changed;
    }

    /**
     * @see ProductData#apply(PriceType, List, BazaarDataOrigin.Snapshot) 
     */
    public boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel> incoming, @NotNull BazaarDataOrigin.Snapshot origin) {
        return apply(transaction.getPriceType(), incoming, origin);
    }

    /**
     * Floor-only variant of {@link #apply(PriceType, List, BazaarDataOrigin.Snapshot)}.
     *
     * Creates absent levels and raises existing ones that fall below the confirmed
     * minimum. Never evicts — a UserPositionEvent source has no authority to assert
     * absence of levels it did not observe.
     */
    public boolean apply(
            @NotNull PriceType type,
            @NotNull List<PriceLevel> incoming,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = bookFor(type);
        boolean changed = false;

        for (var level : incoming) {
            PriceLevel existing = book.get(level.pricePerUnit());

            if (existing == null) {
                book.put(level.pricePerUnit(), level);
                changed = true;
            } else if (existing.acceptsUpdateFrom(origin) && existing.totalVolume() < level.totalVolume()) {
                book.put(level.pricePerUnit(), new PriceLevel(
                        level.pricePerUnit(), level.totalVolume(),
                        Math.max(existing.orderCount(), level.orderCount()), origin));
                changed = true;
            }
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
     * Optimistically increments (or creates) a level when a user order is placed.
     *
     * @return {@code true} if the book was mutated.
     */
    public boolean place(
            @NotNull PriceType type,
            double price,
            int amount,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = bookFor(type);

        boolean[] changed = {!book.containsKey(price)};

        book.merge(price,
                new PriceLevel(price, amount, 1, origin),
                (existing, next) -> {
                    if (!existing.acceptsUpdateFrom(origin)) return existing;
                    changed[0] = true;
                    return existing.withPlacementIncrement(amount, origin.timestamp());
                });

        return changed[0];
    }

    /**
     * @see ProductData#place(PriceType, double, int, BazaarDataOrigin.UserPositionEvent) 
     */
    public boolean place(@NotNull TransactionType transaction, double price, int amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return place(transaction.getPriceType(), price, amount, origin);
    }

    /**
     * Decrements volume at a price level, removing it if volume reaches zero.
     *
     * <p>Restricted to {@link BazaarDataOrigin.UserPositionEvent} — only user-driven
     * mutations (fills, cancels, instant deals, orders-screen reconciliation)
     * should call this. The mutating source is stamped onto the surviving level
     * so that subsequent snapshot eviction rules remain coherent.
     *
     * @return {@code true} if the level existed and was mutated.
     */
    public boolean decrement(
            @NotNull PriceType type,
            double price,
            long amount,
            boolean terminal,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = bookFor(type);
        if (!book.containsKey(price)) return false;

        boolean[] changed = {false};

        book.computeIfPresent(price, (__, pool) -> {
            if (!pool.acceptsUpdateFrom(origin)) return pool;
            changed[0] = true;
            PriceLevel updated = pool.withVolumeDecrement(amount, terminal, origin);
            return updated.totalVolume() <= 0 ? null : updated;
        });

        return changed[0];
    }

    /**
     * @see ProductData#decrement(PriceType, double, long, boolean, BazaarDataOrigin.UserPositionEvent)
     */
    public boolean decrement(@NotNull TransactionType transaction, double price, long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return decrement(transaction.getPriceType(), price, amount, terminal, origin);
    }

    /**
     * Walks price levels in book order (best price first), consuming {@code volume}
     * units across as many levels as needed.
     *
     * <p>Used for instant deals where consumed volume may span multiple price levels.
     * Delegates to {@link #decrement} per level so eviction rules and timestamp
     * stamping remain coherent.
     *
     * @return {@code true} if any level was mutated.
     */
    public boolean consume(
            @NotNull PriceType type,
            long volume,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = bookFor(type);

        long remaining = volume;
        boolean changed = false;

        var keys = new ArrayList<>(book.keySet());
        for (var key : keys) {
            if (remaining <= 0) break;
            var level = book.get(key);
            if (level == null) continue;
            long consuming = Math.min(remaining, level.totalVolume());

            changed |= decrement(type, key, consuming, false, origin);
            remaining -= consuming;
        }

        return changed;
    }

    /**
     * @see ProductData#consume(PriceType, long, BazaarDataOrigin.UserPositionEvent)
     */
    public boolean consume(@NotNull TransactionType transaction, long volume, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return consume(transaction.getPriceType(), volume, origin);
    }

    /**
     * Bounded walk: like {@link #consume(PriceType, long, BazaarDataOrigin.UserPositionEvent)}
     * but stops when the price exceeds {@code priceLimit} in the book's natural order direction.
     *
     * Used for crossing placements — only levels at or better than the placement price
     * should be consumed from the opposite book.
     */
    public boolean consume(
            @NotNull PriceType type,
            long volume,
            double priceLimit,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        var book = bookFor(type);

        long remaining = volume;
        boolean changed = false;

        var keys = new ArrayList<>(book.keySet());
        for (var key : keys) {
            if (remaining <= 0) break;
            var level = book.get(key);
            if (level == null) continue;
            if (level.exceedsBoundary(type, priceLimit)) break;

            long consuming = Math.min(remaining, level.totalVolume());
            changed |= decrement(type, key, consuming, false, origin);
            remaining -= consuming;
        }

        return changed;
    }

    /** @see #consume(PriceType, long, double, BazaarDataOrigin.UserPositionEvent) */
    public boolean consume(
            @NotNull TransactionType transaction,
            long volume,
            double priceLimit,
            @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        return consume(transaction.getPriceType(), volume, priceLimit, origin);
    }

    /**
     * Read-only scan: how many units of {@code volume} would be immediately matched
     * at levels that do not exceed {@code priceLimit} in the given book direction.
     *
     * INSTABUY  (ascending asks):  counts asks at prices <= priceLimit.
     * INSTASELL (descending bids): counts bids at prices >= priceLimit.
     *
     * Does not mutate anything. Called by sources before constructing an OrderDelta
     * so the Order's initial fill state can be set correctly at placement time.
     */
    public long estimateCrossVolume(@NotNull PriceType type, double priceLimit, long volume) {
        var book = bookFor(type);
        long remaining = volume;

        for (var entry : book.entrySet()) {
            if (remaining <= 0) break;
            if (entry.getValue().exceedsBoundary(type, priceLimit)) break;
            remaining -= Math.min(remaining, entry.getValue().totalVolume());
        }

        return volume - remaining;
    }
}