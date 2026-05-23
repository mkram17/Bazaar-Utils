package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Mutable per-product order book, holding bid and ask price levels.
 *
 * <p>Book orientation:
 * <ul>
 *   <li>{@code asksBook} — sell offers (asks), ascending; {@code firstEntry()} = lowest ask.</li>
 *   <li>{@code bidsBook} — buy orders (bids), descending; {@code firstEntry()} = highest bid.</li>
 * </ul>
 *
 * <p>Not thread-safe for compound operations. {@link java.util.TreeMap} operations are not
 * atomic across multiple reads; callers must not rely on cross-call consistency without
 * external synchronization.
 */
@Getter
public final class ProductData implements ProductInfo {
    /**
     * Maximum number of price levels the Hypixel API returns per side per product.
     * When a snapshot contains fewer than this many levels, it is exhaustive —
     * absence of a price anywhere in the book is authoritative, not just within
     * the observed [lo, hi] window.
     */
    public static final int API_DEPTH = 30;

    @NotNull
    private final String productId;

    // sell offers (asks) — natural order,  firstEntry = lowest ask
    // buy orders  (bids) — reverse order,  firstEntry = highest bid
    private final NavigableMap<Double, PriceLevel> asksBook = new TreeMap<>();
    private final NavigableMap<Double, PriceLevel> bidsBook = new TreeMap<>(Comparator.reverseOrder());

    private volatile long lastApiUpdateTs = -1;

    /** {@code true} after at least one API snapshot has been processed this session. */
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

    public ProductData(@NotNull String productId) {
        this.productId = productId;
    }

    /** Returns the book for the given price type. */
    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull PriceType type) {
        return type == PriceType.INSTABUY ? asksBook : bidsBook;
    }

    /** @see #bookFor(PriceType) */
    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull TransactionType transaction) {
        return transaction.getPriceType() == PriceType.INSTABUY ? asksBook : bidsBook;
    }

    /**
     * Returns the number of price levels strictly ahead of {@code price} in the given book.
     * Zero means this price is at the best available position; any positive value means at
     * least one order at a better price exists.
     */
    public int positionOf(@NotNull PriceType type, double price) {
        return bookFor(type).headMap(price).size();
    }

    /** @see #positionOf(PriceType, double) */
    public int positionOf(@NotNull TransactionType transaction, double price) {
        return bookFor(transaction).headMap(price).size();
    }

    /**
     * Merges a snapshot into the book, then evicts levels absent from the snapshot's
     * authoritative scope.
     *
     * <h3>Merge</h3>
     * Each level in {@code incoming} is applied via {@link PriceLevel#isSupersededBy}.
     *
     * <h3>Eviction</h3>
     * Levels absent from {@code incoming} are removed depending on their origin and the
     * snapshot's coverage:
     * <ul>
     *   <li><b>Snapshot-originated</b>: an {@link BazaarDataOrigin.ApiSnapshot} evicts any
     *       stale level it omits. A {@link BazaarDataOrigin.PageSummary} evicts only within
     *       its observed price range.</li>
     *   <li><b>{@link BazaarDataOrigin.UserPositionEvent}-originated</b>: only an
     *       {@link BazaarDataOrigin.ApiSnapshot} can evict them. When the snapshot returned
     *       fewer than {@link #API_DEPTH} levels the side was exhaustively observed and any
     *       absent level is evicted unconditionally; otherwise only levels within the observed
     *       price window are evicted.</li>
     * </ul>
     * A level stamped at or after {@code origin.timestamp()} is never evicted — the API
     * pipeline has non-zero latency and may not have observed a very recently placed order.
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

    /** @see #apply(PriceType, List, BazaarDataOrigin.Snapshot) */
    public boolean apply(@NotNull TransactionType transaction, @NotNull List<PriceLevel> incoming, @NotNull BazaarDataOrigin.Snapshot origin) {
        return apply(transaction.getPriceType(), incoming, origin);
    }

    /**
     * Upserts levels from a {@link BazaarDataOrigin.UserPositionEvent} source: creates absent
     * levels and raises existing ones that fall below the confirmed minimum volume.
     * Never evicts — a user-position event has no authority to assert absence.
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

    /** @see #place(PriceType, double, int, BazaarDataOrigin.UserPositionEvent) */
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

    /** @see #decrement(PriceType, double, long, boolean, BazaarDataOrigin.UserPositionEvent) */
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

    /** @see #consume(PriceType, long, BazaarDataOrigin.UserPositionEvent) */
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
     * Returns how many units of {@code volume} would be immediately crossed at levels that
     * do not exceed {@code priceLimit} in the book's natural order direction.
     *
     * <p>INSTABUY (ascending asks): counts asks at {@code price <= priceLimit}.
     * INSTASELL (descending bids): counts bids at {@code price >= priceLimit}.
     * Does not mutate.
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