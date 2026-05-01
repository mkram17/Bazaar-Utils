package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.*;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import org.jetbrains.annotations.Nullable;

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
    MutationOutcome apply(String productId, O origin);

    /** True no-op, for either kind of origin. */
    record None<O extends BazaarDataOrigin>() implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.get(productId);



            return data == null
                    ? MutationOutcome.unregistered()
                    : MutationOutcome.confirmed(false, data);
        }
    }

    /**
     * Optimistic increment when a user places a new order.
     * Calls {@link BazaarDataRegistry#getOrCreate} — creates the product entry if absent.
     */
    record Place<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, double price, int amount) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.getOrCreate(productId);

            return MutationOutcome.confirmed(data.place(type, price, amount, origin), data);
        }
    }

    /**
     * Decrement when volume leaves the book (fill, cancel, instant deal at a single level).
     * Calls {@link BazaarDataRegistry#get} — silently no-ops if product is unknown.
     */
    record Decrement<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, double price, int amount, boolean terminal) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.get(productId);
            if (data == null) return MutationOutcome.unregistered();

            boolean changed = data.decrement(type, price, amount, terminal, origin);

            return terminal && changed
                    ? MutationOutcome.withEviction(true, data, type, new AbsenceScope.Exact(price))
                    : MutationOutcome.confirmed(changed, data);
        }
    }

    /**
     * Walks price levels in book order (best price first) under {@code op}'s policy —
     * see {@link ProductData.WalkOp}. Covers instant-deal consumption, order-placement crossing,
     * and evict-ahead.
     */
    record Walk<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, ProductData.WalkOp op) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.get(productId);
            if (data == null) return MutationOutcome.unregistered();

            boolean changed = data.walk(type, op, origin);

            return op.terminalScope() != null && changed
                    ? MutationOutcome.withEviction(changed, data, type, op.terminalScope())
                    : MutationOutcome.confirmed(changed, data);
        }
    }

    /**
     * Floor-only affirmation: ensures confirmed active-order levels exist at or above
     * their known volume, without ever evicting. Delegates to the
     * {@link BazaarDataOrigin.UserPositionEvent} overload of {@code ProductData.apply}.
     */
    record Floor<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> levels) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.get(productId);

            return data == null ? MutationOutcome.unregistered() : MutationOutcome.confirmed(data.apply(type, levels, origin), data);
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
    record Splice<O extends BazaarDataOrigin.Snapshot>(PriceType type, List<PriceLevel<O>> levels) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var data = BazaarDataRegistry.getOrCreate(productId);

            var coverage = AbsenceScope.Window.of(origin, levels);

            boolean changed = data.apply(type, levels, origin, coverage);

            return MutationOutcome.withEviction(changed, data, type, coverage);
        }
    }

    /**
     * An ordered sequence of same-{@code O} mutations, each applied in turn regardless
     * of whether an earlier one succeeded. Produced by {@link #then} — prefer that
     * over direct construction.
     */
    record Compound<O extends BazaarDataOrigin>(List<BookMutation<O>> mutations) implements BookMutation<O> {
        @Override
        public MutationOutcome apply(String productId, O origin) {
            var outcome = mutations.getFirst().apply(productId, origin);

            for (var mutation : mutations.subList(1, mutations.size())) outcome = outcome.merge(mutation.apply(productId, origin));

            return outcome;
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
        return new None<>();
    }

    /** Returns a {@link Place}, or {@link #none()} when {@code amount} is not positive. */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> place(PriceType type, double price, int amount) {
        if (amount <= 0) return none();

        return new Place<>(type, price, amount);
    }

    /** @see #place(PriceType, double, int) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> place(TransactionType transaction, double price, int amount) {
        return place(transaction.getPriceType(), price, amount);
    }

    /**
     * Returns a {@link Decrement}. Pass {@code terminal = true} when the order will
     * produce no further fills at this level (fill complete, cancel, expiry).
     */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> decrement(PriceType type, double price, int amount, boolean terminal) {
        return new Decrement<>(type, price, amount, terminal);
    }

    /** @see #decrement(PriceType, double, int, boolean) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> decrement(TransactionType transaction, double price, int amount, boolean terminal) {
        return decrement(transaction.getPriceType(), price, amount, terminal);
    }

    /** Returns a {@link Walk} that consumes up to {@code volume} at any price, or {@link #none()} when not positive. */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> walk(PriceType type, long volume) {
        if (volume <= 0) return none();

        return new Walk<>(type, ProductData.WalkOp.upTo(volume));
    }

    /** @see #walk(PriceType, long) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> walk(TransactionType transaction, long volume) {
        return walk(transaction.getPriceType(), volume);
    }

    /** Returns a {@link Walk} bounded at {@code priceLimit}, or {@link #none()} when {@code volume} is not positive. */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> walkUpTo(PriceType type, long volume, double priceLimit) {
        if (volume <= 0) return none();

        return new Walk<>(type, ProductData.WalkOp.upTo(volume, priceLimit));
    }

    /** @see #walkUpTo(PriceType, long, double) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> walkUpTo(TransactionType transaction, long volume, double priceLimit) {
        return walkUpTo(transaction.getPriceType(), volume, priceLimit);
    }

    /**
     * Returns a {@link Walk} that drains every level strictly better-priced than
     * {@code price} — see {@link ProductData.WalkOp#ahead}. Always attempted.
     */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> evictAhead(PriceType type, double price) {
        return new Walk<>(type, ProductData.WalkOp.ahead(price));
    }

    /** @see #evictAhead(PriceType, double) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> evictAhead(TransactionType transaction, double price) {
        return evictAhead(transaction.getPriceType(), price);
    }

    /** Returns a {@link Floor}, or {@link #none()} when {@code levels} is empty. */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> floor(PriceType type, List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> levels) {
        if (levels.isEmpty()) return none();

        return new Floor<>(type, levels);
    }

    /** @see #floor(PriceType, List) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> floor(TransactionType transaction, List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> levels) {
        return floor(transaction.getPriceType(), levels);
    }

    /**
     * Returns a {@link Splice} for {@code levels} on {@code type}'s side. Always
     * constructs — never collapses an empty list to {@link #none()}; see
     * {@link Splice}'s doc.
     */
    static <O extends BazaarDataOrigin.Snapshot> BookMutation<O> splice(PriceType type, List<PriceLevel<O>> levels) {
        return new Splice<>(type, levels);
    }

    /** @see #splice(PriceType, List) */
    static <O extends BazaarDataOrigin.Snapshot> BookMutation<O> splice(TransactionType transaction, List<PriceLevel<O>> levels) {
        return splice(transaction.getPriceType(), levels);
    }

    /**
     * The book state and eviction claims produced by applying one {@link BookMutation}
     * — or, via {@link Compound}, a whole chain of them — to one product.
     *
     * @param book {@code null} exactly when the touched product was never registered
     *             and this mutation had no reason to create it: {@link Decrement},
     *             {@link Walk}, and {@link Floor} all no-op on an unknown product
     *             rather than calling {@link BazaarDataRegistry#getOrCreate}.
     */
    record MutationOutcome(
            boolean changed,
            @Nullable ProductData book,
            AbsenceScope instabuyEviction,
            AbsenceScope instasellEviction
    ) {
        /** Found (or created) the product, but this mutation makes no eviction claim of its own. */
        static MutationOutcome confirmed(boolean changed, ProductData data) {
            return new MutationOutcome(changed, data, AbsenceScope.NONE, AbsenceScope.NONE);
        }

        /** The product was never registered and this mutation had no standing to create it. */
        static MutationOutcome unregistered() {
            return new MutationOutcome(false, null, AbsenceScope.NONE, AbsenceScope.NONE);
        }

        static MutationOutcome withEviction(boolean changed, ProductData data, PriceType type, AbsenceScope scope) {
            return type == PriceType.INSTABUY
                    ? new MutationOutcome(changed, data, scope, AbsenceScope.NONE)
                    : new MutationOutcome(changed, data, AbsenceScope.NONE, scope);
        }

        /** Whether any accumulated eviction claim proves {@code price} absent on {@code type}'s side. */
        public boolean canEvict(PriceType type, double price) {
            return (type == PriceType.INSTABUY ? instabuyEviction : instasellEviction).contains(type, price);
        }

        /**
         * Folds {@code next} into {@code this} for a {@link Compound} chain.
         * Short-circuits to {@code this} unchanged when {@code next} came from an
         * unregistered product — safe because {@link #unregistered()} always pairs a
         * {@code null} book with {@code changed = false} and no evictions, so there's
         * nothing in {@code next} worth folding in.
         */
        MutationOutcome merge(MutationOutcome next) {
            if (next.book() == null) return this;

            return new MutationOutcome(
                    changed || next.changed(),
                    next.book(),
                    preferReal(instabuyEviction, next.instabuyEviction()),
                    preferReal(instasellEviction, next.instasellEviction())
            );
        }

        private static AbsenceScope preferReal(AbsenceScope prior, AbsenceScope next) {
            return next instanceof AbsenceScope.None ? prior : next;
        }
    }
}