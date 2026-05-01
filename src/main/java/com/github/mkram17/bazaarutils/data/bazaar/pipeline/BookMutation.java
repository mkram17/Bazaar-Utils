package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Algebraic type encoding the complete vocabulary of book mutations a data source can produce.
 *
 * <p>Each variant carries just enough information to execute against a named product's book via
 * {@link #apply}. Sources compose mutations using the static factory methods (which suppress
 * zero-volume no-ops) and chain them with {@link #then}. The composed mutation is applied once
 * atomically before any storage write.
 *
 * <p>All variants are {@link BazaarDataOrigin.UserPositionEvent}-scoped: they create or adjust
 * levels but never assert absence. Absence is the exclusive domain of
 * {@link BazaarDataOrigin.Snapshot} sources via
 * {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData#apply(com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType, java.util.List, BazaarDataOrigin.Snapshot)}.
 */
public sealed interface BookMutation permits
        BookMutation.None,
        BookMutation.Place,
        BookMutation.Decrement,
        BookMutation.Walk,
        BookMutation.BoundedWalk,
        BookMutation.Floor,
        BookMutation.Compound {

    BookMutation NONE = new None();

    /**
     * Applies this mutation to the book for {@code productId}.
     *
     * <p>All {@link BazaarDataRegistry} lookups are owned here — sources never
     * access the registry directly.
     *
     * @return {@code true} if the book changed.
     */
    boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin);

    record None() implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            return false;
        }
    }

    /**
     * Optimistic increment when a user places a new order.
     * Calls {@link BazaarDataRegistry#getOrCreate} — creates the product entry if absent.
     */
    record Place(PriceType type, double price, int amount) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            return BazaarDataRegistry.getOrCreate(productId).place(type, price, amount, origin);
        }
    }

    /**
     * Decrement when volume leaves the book (fill, cancel, instant deal at a single level).
     * Calls {@link BazaarDataRegistry#get} — silently no-ops if product is unknown.
     */
    record Decrement(
            PriceType type,
            double price,
            int amount,
            boolean terminal
    ) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.decrement(type, price, amount, terminal, origin);
        }
    }

    /**
     * Walks price levels in book order, consuming {@code volume} units across as many
     * levels as needed. Used exclusively by instant deals — no tracked order is involved
     * and the consumed volume may span multiple levels.
     */
    record Walk(PriceType type, long volume) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.consume(type, volume, origin);
        }
    }

    /**
     * Consumes opposite-book levels that a newly placed order immediately crosses,
     * stopping at {@code priceLimit}. Used for order-placement price-crossing only —
     * not interchangeable with {@link Walk} (the unbounded instant-deal path).
     */
    record BoundedWalk(PriceType type, long volume, double priceLimit) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.consume(type, volume, priceLimit, origin);
        }
    }

    /**
     * Floor-only affirmation: ensures confirmed active order levels exist at or above
     * their known volume. Delegates to the {@link BazaarDataOrigin.UserPositionEvent}
     * overload of {@code ProductData.apply}, which creates absent levels and raises
     * existing ones that fall below the confirmed minimum — it never evicts.
     *
     * <p>Used by the orders-screen reconciliation path to restore levels that may have
     * been evicted by an independent snapshot between screen loads, with no corresponding
     * order-state change to trigger a re-placement.
     *
     * <p>Composed into the same {@link BookMutation} chain as eviction decrements and
     * fill decrements so that one {@code apply()} call covers all screen-driven book
     * effects atomically.
     */
    record Floor(PriceType type, List<PriceLevel> levels) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.apply(type, levels, origin);
        }
    }

    /**
     * A flat, ordered sequence of mutations. All members execute regardless of individual
     * success. Produced by {@link #then} — prefer that over direct construction.
     */
    record Compound(List<BookMutation> mutations) implements BookMutation {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            boolean changed = false;

            for (var mutation : mutations) changed |= mutation.apply(productId, origin);

            return changed;
        }
    }

    /**
     * Returns a mutation that applies {@code this} then {@code next}. A {@link None} operand on
     * either side is elided. Adjacent {@link Compound} lists are flattened to a single level.
     */
    default BookMutation then(BookMutation next) {
        if (next instanceof None) return this;
        if (this instanceof None) return next;

        var left = this instanceof Compound(var mutations) ? mutations : List.of(this);
        var right = next instanceof Compound(var mutations) ? mutations : List.of(next);
        var merged = new ArrayList<BookMutation>(left.size() + right.size());

        merged.addAll(left);
        merged.addAll(right);

        return new Compound(merged);
    }

    /** Returns a {@link Place}, or {@link #NONE} when {@code amount} is not positive. */
    static BookMutation place(PriceType type, double price, int amount) {
        return amount > 0 ? new Place(type, price, amount) : NONE;
    }

    static BookMutation place(TransactionType transaction, double price, int amount) {
        return place(transaction.getPriceType(), price, amount);
    }

    /**
     * Returns a {@link Decrement}. Pass {@code terminal = true} when the order will produce no
     * further fills at this level (fill complete, cancel, expiry).
     */
    static BookMutation decrement(PriceType type, double price, int amount, boolean terminal) {
        return new Decrement(type, price, amount, terminal);
    }

    static BookMutation decrement(TransactionType transaction, double price, int amount, boolean terminal) {
        return decrement(transaction.getPriceType(), price, amount, terminal);
    }

    /** Returns a {@link Walk}, or {@link #NONE} when {@code volume} is not positive. */
    static BookMutation walk(PriceType type, long volume) {
        return volume > 0 ? new Walk(type, volume) : NONE;
    }

    static BookMutation walk(TransactionType transaction, long volume) {
        return walk(transaction.getPriceType(), volume);
    }

    /** Returns a {@link BoundedWalk}, or {@link #NONE} when {@code volume} is not positive. */
    static BookMutation walkUpTo(PriceType type, long volume, double priceLimit) {
        return volume > 0 ? new BoundedWalk(type, volume, priceLimit) : NONE;
    }

    static BookMutation walkUpTo(TransactionType transaction, long volume, double priceLimit) {
        return walkUpTo(transaction.getPriceType(), volume, priceLimit);
    }

    /** Returns a {@link Floor}, or {@link #NONE} when {@code levels} is empty. */
    static BookMutation floor(PriceType type, List<PriceLevel> levels) {
        return levels.isEmpty() ? NONE : new Floor(type, levels);
    }

    static BookMutation floor(TransactionType transaction, List<PriceLevel> levels) {
        return floor(transaction.getPriceType(), levels);
    }
}