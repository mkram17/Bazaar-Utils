package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

@Getter
public final class ProductData implements ProductInfo {
    @NotNull
    private final String productId;

    // buyBook  = sell offers (asks) — natural order,  firstEntry = lowest ask
    // sellBook = buy orders  (bids) — reverse order,  firstEntry = highest bid
    private final NavigableMap<Double, PriceLevel> buyBook = new ConcurrentSkipListMap<>();
    private final NavigableMap<Double, PriceLevel> sellBook = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    public volatile long lastApiUpdateTs = -1;

    public ProductData(@NotNull String productId) {
        this.productId = productId;
    }

    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull TransactionType.Side side) {
        return side == TransactionType.Side.BUY ? buyBook : sellBook;
    }

    public @NotNull NavigableMap<Double, PriceLevel> bookFor(@NotNull TransactionType transaction) {
        return transaction.getPriceType() == PriceType.INSTABUY ? buyBook : sellBook;
    }

    /**
     * How many price levels are strictly ahead of the given price — 0 means competitive.
     */
    public int positionOf(@NotNull TransactionType.Side side, double price) {
        return bookFor(TransactionType.of(side, TransactionType.Method.ORDER)).headMap(price).size();
    }

    public int positionOf(@NotNull TransactionType transaction, double price) {
        return bookFor(transaction).headMap(price).size();
    }

    /**
     * Merges a market snapshot into the book.
     *
     * <p>Only {@link DataSources.Snapshot} sources may call this — the
     * type signature enforces that at the call site. Two effects:
     * <ol>
     *   <li>Every level present in {@code incoming} is merged using
     *       {@link PriceLevel#isSupersededBy} to decide which wins.</li>
     *   <li>Levels absent from {@code incoming} are evicted if the snapshot is
     *       authoritative over them (see inline comments).</li>
     * </ol>
     *
     * @return {@code true} if any level was added, replaced, or evicted.
     */
    public boolean apply(
            @NotNull TransactionType.Side side,
            @NotNull List<PriceLevel> incoming,
            @NotNull DataSources.Snapshot source) {

        var book = bookFor(side);
        boolean changed = false;
        var incomingPrices = new HashSet<Double>(incoming.size());

        // 1. Insert / supersede
        for (PriceLevel next : incoming) {
            incomingPrices.add(next.pricePerUnit());

            PriceLevel[] chosen = {null};
            book.merge(next.pricePerUnit(), next, (current, it) -> {
                if (current.isSupersededBy(it)) {
                    chosen[0] = it;
                    return it;
                }
                chosen[0] = current;
                return current;
            });

            if (chosen[0] == null || chosen[0] == next) changed = true;
        }

        // 2. Evict absent levels within the source's authoritative scope
        changed |= book.entrySet().removeIf(entry -> {
            var level = entry.getValue();
            double key = entry.getKey();

            if (incomingPrices.contains(key)) return false;
            // Timestamp guard: never evict a level that is at least as new as
            // this snapshot — the API simply hasn't observed it yet.
            if (level.source().timestamp() >= source.timestamp()) return false;

            return switch (level.source()) {
                // Snapshot levels
                // ApiSnapshot (full-depth): evicts all stale market levels.
                // PageSummary (top-N): evicts only within its observed price range.
                case DataSources.Snapshot ignored -> {
                    if (source instanceof DataSources.ApiSnapshot) yield true;
                    if (incomingPrices.isEmpty()) yield false;
                    double lo = Collections.min(incomingPrices);
                    double hi = Collections.max(incomingPrices);
                    yield key >= lo && key <= hi;
                }

                // UserPositionEvent levels (OrderPlaced, post-decrement stamps, etc.)
                // PageSummary cannot make absence assertions about user-placed levels
                // outside its top-N window. Only a full-depth ApiSnapshot is
                // authoritative enough to evict them — and the timestamp guard above
                // already protects freshly placed orders the API hasn't seen yet.
                case DataSources.UserPositionEvent ignored -> source instanceof DataSources.ApiSnapshot;
            };
        });

        return changed;
    }

    /**
     * Optimistically increments (or creates) a level when a user order is placed.
     * Typed to {@link DataSources.OrderPlaced} — only the placement data source
     * should call this.
     *
     * @return {@code true} if the book was mutated.
     */
    public boolean place(
            @NotNull TransactionType.Side side,
            double price,
            int amount,
            @NotNull DataSources.UserPositionEvent source) {
        var book = bookFor(TransactionType.of(side, TransactionType.Method.ORDER));
        boolean[] changed = {!book.containsKey(price)};

        book.merge(price,
                new PriceLevel(price, amount, 1, source.timestamp(), source),
                (existing, next) -> {
                    if (!existing.acceptsUpdateFrom(source)) return existing;
                    changed[0] = true;
                    return existing.withPlacementIncrement(amount, source.timestamp());
                });

        return changed[0];
    }

    /**
     * Decrements volume at a price level, removing it if volume reaches zero.
     *
     * <p>Typed to {@link DataSources.UserPositionEvent} — only user-driven
     * mutations (fills, cancels, instant deals, orders-screen reconciliation)
     * should call this. The mutating source is stamped onto the surviving level
     * so that subsequent snapshot eviction rules remain coherent.
     *
     * @return {@code true} if the level existed and was mutated.
     */
    public boolean decrement(
            @NotNull TransactionType.Side side,
            double price,
            long amount,
            @NotNull DataSources.UserPositionEvent source) {

        var book = bookFor(TransactionType.of(side, TransactionType.Method.ORDER));
        if (!book.containsKey(price)) return false;

        boolean[] changed = {false};

        book.computeIfPresent(price, (__, pool) -> {
            if (!pool.acceptsUpdateFrom(source)) return pool;
            changed[0] = true;
            PriceLevel updated = pool.withVolumeDecrement(amount, source);
            return updated.totalVolume() <= 0 ? null : updated;
        });

        return changed[0];
    }
}