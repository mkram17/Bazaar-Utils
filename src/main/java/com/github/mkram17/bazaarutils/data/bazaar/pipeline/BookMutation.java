package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Algebraic type encoding the complete vocabulary of book mutations a data source can
 * produce.
 *
 * <p>Sources compose mutations using the static factory methods and chain them with
 * {@link #then}. Most factories collapse a no-op case to {@link #none()} —
 * {@link Splice} deliberately does not, since even an empty read can have real
 * eviction consequences; see its own doc. The composed mutation is applied once,
 * atomically, before any storage write.
 */
public sealed interface BookMutation<O extends BazaarDataOrigin> permits
        BookMutation.None,
        BookMutation.Place,
        BookMutation.Decrement,
        BookMutation.Walk,
        BookMutation.Floor,
        BookMutation.Splice,
        BookMutation.Compound {
    /**
     * Applies this mutation to the book for {@code productId}.
     *
     * <p>All {@link BazaarDataRegistry} lookups are owned here — sources never
     * access the registry directly, for either kind of write this interface covers.
     *
     * @return {@code true} if the book changed.
     */
    boolean apply(String productId, O origin);

    /** True no-op, for either kind of origin. */
    record None<O extends BazaarDataOrigin>() implements BookMutation<O> {
        @Override
        public boolean apply(String productId, O origin) {
            return false;
        }
    }

    /**
     * Optimistic increment when a user places a new order.
     * Calls {@link BazaarDataRegistry#getOrCreate} — creates the product entry if absent.
     */
    record Place(PriceType type, double price, int amount) implements BookMutation<BazaarDataOrigin.UserPositionEvent> {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            return BazaarDataRegistry.getOrCreate(productId).place(type, price, amount, origin);
        }
    }

    /**
     * Decrement when volume leaves the book (fill, cancel, instant deal at a single level).
     * Calls {@link BazaarDataRegistry#get} — silently no-ops if product is unknown.
     */
    record Decrement(PriceType type, double price, int amount, boolean terminal) implements BookMutation<BazaarDataOrigin.UserPositionEvent> {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.decrement(type, price, amount, terminal, origin);
        }
    }

    /**
     * Walks price levels in book order (best price first) under {@code op}'s policy —
     * see {@link ProductData.WalkOp}. Covers instant-deal consumption, order-placement crossing,
     * and evict-ahead.
     */
    record Walk(PriceType type, ProductData.WalkOp op) implements BookMutation<BazaarDataOrigin.UserPositionEvent> {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.walk(type, op, origin);
        }
    }

    /**
     * Floor-only affirmation: ensures confirmed active-order levels exist at or above
     * their known volume, without ever evicting. Delegates to the
     * {@link BazaarDataOrigin.UserPositionEvent} overload of {@code ProductData.apply}.
     */
    record Floor(PriceType type, List<PriceLevel> levels) implements BookMutation<BazaarDataOrigin.UserPositionEvent> {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.UserPositionEvent origin) {
            var data = BazaarDataRegistry.get(productId);

            return data != null && data.apply(type, levels, origin);
        }
    }

    /**
     * Splices a {@link BazaarDataOrigin.Snapshot} read into one side of a product's
     * book — the one variant that asserts absence, since that's a market read's whole
     * purpose.
     *
     * <p>Its factory never collapses an empty {@code levels} list to {@link #none()}:
     * {@link BazaarDataOrigin.Snapshot#isExhaustive} can be {@code true} at
     * {@code reportedCount == 0}, and an empty, exhaustive read still has real
     * eviction consequences that collapsing to a no-op would silently suppress.
     */
    record Splice(PriceType type, List<PriceLevel> levels) implements BookMutation<BazaarDataOrigin.Snapshot> {
        @Override
        public boolean apply(String productId, BazaarDataOrigin.Snapshot origin) {
            return BazaarDataRegistry.getOrCreate(productId).apply(type, levels, origin);
        }
    }

    /**
     * An ordered sequence of same-{@code O} mutations, each applied in turn regardless
     * of whether an earlier one succeeded. Produced by {@link #then} — prefer that
     * over direct construction.
     */
    record Compound<O extends BazaarDataOrigin>(List<BookMutation<O>> mutations) implements BookMutation<O> {
        @Override
        public boolean apply(String productId, O origin) {
            boolean changed = false;
            for (var mutation : mutations) changed |= mutation.apply(productId, origin);
            return changed;
        }
    }

    /**
     * Returns a mutation that applies {@code this} then {@code next}. A {@link None} operand on
     * either side is elided. Adjacent {@link Compound} lists are flattened to a single level.
     */
    default BookMutation<O> then(BookMutation<O> next) {
        if (next instanceof None) return this;
        if (this instanceof None) return next;

        List<BookMutation<O>> left = this instanceof Compound(var mutations) ? mutations : List.of(this);
        List<BookMutation<O>> right = next instanceof Compound(var mutations) ? mutations : List.of(next);

        var merged = new ArrayList<BookMutation<O>>(left.size() + right.size());

        merged.addAll(left);
        merged.addAll(right);

        return new Compound<>(merged);
    }

    /** The shared no-op, typed for whichever {@code O} the caller needs. */
    static <O extends BazaarDataOrigin> BookMutation<O> none() {
        return new None<O>();
    }

    /** Returns a {@link Place}, or {@link #none()} when {@code amount} is not positive. */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> place(PriceType type, double price, int amount) {
        if (amount <= 0) return none();

        return new Place(type, price, amount);
    }

    /** @see #place(PriceType, double, int) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> place(TransactionType transaction, double price, int amount) {
        return place(transaction.getPriceType(), price, amount);
    }

    /**
     * Returns a {@link Decrement}. Pass {@code terminal = true} when the order will
     * produce no further fills at this level (fill complete, cancel, expiry).
     */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> decrement(PriceType type, double price, int amount, boolean terminal) {
        return new Decrement(type, price, amount, terminal);
    }

    /** @see #decrement(PriceType, double, int, boolean) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> decrement(TransactionType transaction, double price, int amount, boolean terminal) {
        return decrement(transaction.getPriceType(), price, amount, terminal);
    }

    /** Returns a {@link Walk} that consumes up to {@code volume} at any price, or {@link #none()} when not positive. */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> walk(PriceType type, long volume) {
        if (volume <= 0) return none();

        return new Walk(type, ProductData.WalkOp.upTo(volume));
    }

    /** @see #walk(PriceType, long) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> walk(TransactionType transaction, long volume) {
        return walk(transaction.getPriceType(), volume);
    }

    /** Returns a {@link Walk} bounded at {@code priceLimit}, or {@link #none()} when {@code volume} is not positive. */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> walkUpTo(PriceType type, long volume, double priceLimit) {
        if (volume <= 0) return none();

        return new Walk(type, ProductData.WalkOp.upTo(volume, priceLimit));
    }

    /** @see #walkUpTo(PriceType, long, double) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> walkUpTo(TransactionType transaction, long volume, double priceLimit) {
        return walkUpTo(transaction.getPriceType(), volume, priceLimit);
    }

    /**
     * Returns a {@link Walk} that drains every level strictly better-priced than
     * {@code price} — see {@link ProductData.WalkOp#ahead}. Always attempted.
     */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> evictAhead(PriceType type, double price) {
        return new Walk(type, ProductData.WalkOp.ahead(price));
    }

    /** @see #evictAhead(PriceType, double) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> evictAhead(TransactionType transaction, double price) {
        return evictAhead(transaction.getPriceType(), price);
    }

    /** Returns a {@link Floor}, or {@link #none()} when {@code levels} is empty. */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> floor(PriceType type, List<PriceLevel> levels) {
        if (levels.isEmpty()) return none();
        return new Floor(type, levels);
    }

    /** @see #floor(PriceType, List) */
    static BookMutation<BazaarDataOrigin.UserPositionEvent> floor(TransactionType transaction, List<PriceLevel> levels) {
        return floor(transaction.getPriceType(), levels);
    }

    /**
     * Returns a {@link Splice} for {@code levels} on {@code type}'s side. Always
     * constructs — never collapses an empty list to {@link #none()}; see
     * {@link Splice}'s doc.
     */
    static BookMutation<BazaarDataOrigin.Snapshot> splice(PriceType type, List<PriceLevel> levels) {
        return new Splice(type, levels);
    }

    /** @see #splice(PriceType, List) */
    static BookMutation<BazaarDataOrigin.Snapshot> splice(TransactionType transaction, List<PriceLevel> levels) {
        return splice(transaction.getPriceType(), levels);
    }
}