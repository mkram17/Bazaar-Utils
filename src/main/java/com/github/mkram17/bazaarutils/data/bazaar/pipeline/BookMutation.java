package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.*;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;

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
        BookMutation.At,
        BookMutation.Compound {

    /**
     * Applies this mutation to the book for {@code productId}.
     *
     * <p>All {@link BazaarDataRegistry} lookups are owned here — sources never
     * access the registry directly, for either kind of write this interface covers.
     *
     * @return {@link MutationOutcome} outcast of the outcome of this operation
     */
    @NotNull MutationOutcome apply(@NotNull String productId, @NotNull O origin);

    /** True no-op, for either kind of origin. */
    record None<O extends BazaarDataOrigin>() implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.get(productId);

            return data == null ? MutationOutcome.unregistered() : MutationOutcome.of(data);
        }
    }

    /**
     * Optimistic increment when a user places a new order.
     * Calls {@link BazaarDataRegistry#getOrCreate} — creates the product entry if absent.
     */
    record Place<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, double price, int amount) implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.getOrCreate(productId);

            return MutationOutcome.of(data).changed(data.place(type, price, amount, origin));
        }
    }

    /**
     * Decrement when volume leaves the book at a single level.
     * Calls {@link BazaarDataRegistry#get} — silently no-ops if product is unknown.
     *
     * <p>{@code cause} governs whether this contributes to the outcome's
     * {@link AttributedFills}; {@code terminal} governs whether the level's order count
     * drops. The two are independent: a completing fill is both, a partial fill neither,
     * a cancel terminal only.
     */
    record Decrement<O extends BazaarDataOrigin.UserPositionEvent>(
            PriceType type, double price, int amount, boolean terminal, Cause cause) implements BookMutation<O> {
        /** Why volume left a level, and so whether its fill is already attributed to an order. */
        public sealed interface Cause {
            /** The volume traded, and the fill is attributed directly to {@code order}, the one this decrement was built from. */
            record Fill(UUID order) implements Cause {}

            /** The volume was pulled without trading — a cancel, an expiry, a price correction. */
            record Withdrawal() implements Cause {}
        }

        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.get(productId);
            if (data == null) return MutationOutcome.unregistered();

            var outcome = MutationOutcome.of(data).changed(data.decrement(type, price, amount, terminal, origin));

            return cause instanceof Cause.Fill(var order)
                    ? outcome.attributing(AttributedFills.of(type, price, order, amount))
                    : outcome;
        }
    }

    /**
     * Walks price levels in book order (best price first) under {@code op}'s policy —
     * see {@link ProductData.WalkOp}. Covers instant-deal consumption, order-placement crossing,
     * and evict-ahead.
     */
    record Walk<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, ProductData.WalkOp op) implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.get(productId);
            if (data == null) return MutationOutcome.unregistered();

            var outcome = MutationOutcome.of(data).changed(data.walk(type, op, origin));

            return op.terminalScope() != null ? outcome.evicting(type, op.terminalScope()) : outcome;
        }
    }

    /**
     * Floor-only affirmation: ensures confirmed active-order levels exist at or above
     * their known volume, without ever evicting. Delegates to the
     * {@link BazaarDataOrigin.UserPositionEvent} overload of {@code ProductData.apply}.
     */
    record Floor<O extends BazaarDataOrigin.UserPositionEvent>(PriceType type, List<PriceLevel<BazaarDataOrigin.UserPositionEvent>> levels) implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.get(productId);
            if (data == null) return MutationOutcome.unregistered();

            return MutationOutcome.of(data).changed(data.apply(type, levels, origin));
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
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var data = BazaarDataRegistry.getOrCreate(productId);

            var coverage = AbsenceScope.Window.of(origin, levels);

            return MutationOutcome.of(data).changed(data.apply(type, levels, origin, coverage)).evicting(type, coverage);
        }
    }

    /**
     * Applies {@code inner} as of {@code origin} rather than whichever origin the
     * surrounding chain is applied with.
     */
    record At<O extends BazaarDataOrigin>(O origin, BookMutation<O> inner) implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O ignored) {
            return inner.apply(productId, origin);
        }
    }

    /**
     * An ordered sequence of same-{@code O} mutations, each applied in turn regardless
     * of whether an earlier one succeeded. Produced by {@link #then} — prefer that
     * over direct construction.
     */
    record Compound<O extends BazaarDataOrigin>(List<BookMutation<O>> mutations) implements BookMutation<O> {
        @Override
        public @NonNull MutationOutcome apply(@NonNull String productId, @NonNull O origin) {
            var outcome = MutationOutcome.unregistered();

            for (var mutation : mutations) outcome = outcome.merge(mutation.apply(productId, origin));

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

    /** Returns an {@link At}, or {@code inner} unchanged when there is nothing to rebase. */
    static <O extends BazaarDataOrigin> BookMutation<O> at(O origin, BookMutation<O> inner) {
        return inner instanceof None ? inner : new At<>(origin, inner);
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
     * Returns a {@link Decrement} for volume that traded, attributed directly to {@code order}.
     * Pass {@code terminal = true} when the fill completes the order, removing it from the level.
     */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> filled(
            UUID order, PriceType type, double price, int amount, boolean terminal) {
        return new Decrement<>(type, price, amount, terminal, new Decrement.Cause.Fill(order));
    }

    /** @see #filled(UUID, PriceType, double, int, boolean) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> filled(
            UUID order, TransactionType transaction, double price, int amount, boolean terminal) {
        return filled(order, transaction.getPriceType(), price, amount, terminal);
    }

    /** Returns a {@link Decrement} for volume pulled without trading. Always terminal, never attributed. */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> withdrawn(
            PriceType type, double price, int amount) {
        return new Decrement<>(type, price, amount, true, new Decrement.Cause.Withdrawal());
    }

    /** @see #withdrawn(PriceType, double, int) */
    static <O extends BazaarDataOrigin.UserPositionEvent> BookMutation<O> withdrawn(
            TransactionType transaction, double price, int amount) {
        return withdrawn(transaction.getPriceType(), price, amount);
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
     */
    record MutationOutcome(
            boolean changed,
            @Nullable ProductData book,
            EvictionClaims evictions,
            AttributedFills attributed
    ) {
        record EvictionClaims(List<AbsenceScope> instabuy, List<AbsenceScope> instasell) {
            static final EvictionClaims NONE = new EvictionClaims(List.of(), List.of());

            EvictionClaims with(PriceType type, AbsenceScope scope) {
                return type == PriceType.INSTABUY
                        ? new EvictionClaims(append(instabuy, scope), instasell)
                        : new EvictionClaims(instabuy, append(instasell, scope));
            }

            boolean contains(PriceType type, double price) {
                return (type == PriceType.INSTABUY ? instabuy : instasell).stream().anyMatch(s -> s.contains(type, price));
            }

            EvictionClaims merge(EvictionClaims next) {
                return new EvictionClaims(union(instabuy, next.instabuy), union(instasell, next.instasell));
            }

            private static List<AbsenceScope> append(List<AbsenceScope> list, AbsenceScope scope) {
                var merged = new ArrayList<>(list);

                merged.add(scope);

                return List.copyOf(merged);
            }

            private static List<AbsenceScope> union(List<AbsenceScope> a, List<AbsenceScope> b) {
                if (b.isEmpty()) return a;
                if (a.isEmpty()) return b;

                var merged = new ArrayList<AbsenceScope>(a.size() + b.size());
                merged.addAll(a);
                merged.addAll(b);

                return List.copyOf(merged);
            }
        }

        public sealed interface LevelObservation permits LevelObservation.Present, LevelObservation.ProvenAbsent, LevelObservation.Unknown {
            record Present(PriceLevel<?> level) implements LevelObservation {}
            record ProvenAbsent() implements LevelObservation {}
            record Unknown() implements LevelObservation {}
        }

        public LevelObservation observe(PriceType type, double price) {
            if (book == null) return new LevelObservation.Unknown();

            var level = book.entryAt(type, price).flatMap(LevelReconciliation::tradable).orElse(null);
            if (level != null) return new LevelObservation.Present(level);

            return canEvict(type, price) ? new LevelObservation.ProvenAbsent() : new LevelObservation.Unknown();
        }

        static MutationOutcome of(@Nullable ProductData book) {
            return new MutationOutcome(false, book, EvictionClaims.NONE, AttributedFills.NONE);
        }

        MutationOutcome changed(boolean changed) {
            return new MutationOutcome(changed, book, evictions, attributed);
        }

        MutationOutcome evicting(PriceType type, AbsenceScope scope) {
            return new MutationOutcome(changed, book, evictions.with(type, scope), attributed);
        }

        MutationOutcome attributing(AttributedFills fills) {
            return new MutationOutcome(changed, book, evictions, attributed.merge(fills));
        }

        /** The product was never registered and this mutation had no standing to create it. */
        static MutationOutcome unregistered() {
            return new MutationOutcome(false, null, EvictionClaims.NONE, AttributedFills.NONE);
        }

        /** Whether any accumulated eviction claim proves {@code price} absent on {@code type}'s side. */
        public boolean canEvict(PriceType type, double price) {
            return evictions.contains(type, price);
        }

        MutationOutcome merge(MutationOutcome next) {
            return new MutationOutcome(changed || next.changed(), next.book() != null ? next.book() : book, evictions.merge(next.evictions()), attributed.merge(next.attributed()));
        }
    }

    /**
     * Fill volume this mutation has already attributed directly, and to which orders,
     * keyed by the level it left. The volume is subtracted from a level's apparent
     * shortfall before {@link FillInference} may allocate against it, so volume one
     * source already attributed cannot be inferred a second time onto a sibling resting
     * at the same price; the attributed orders are the ones a residual shortfall at that
     * level avoids landing on.
     *
     * <p>Only a {@link Decrement} carrying {@link Decrement.Cause.Fill} contributes. A
     * cancel, an expiry, or a price correction removes the order's own expected volume
     * alongside the level's, leaving the shortfall already correct. A crossing placement
     * takes its volume from the opposite side's levels, never its own, so it contributes
     * nothing either.
     */
    record AttributedFills(Map<PriceGroup, Attribution> byLevel) {
        public static final AttributedFills NONE = new AttributedFills(Map.of());

        /**
         * What was attributed at one level: {@code volume} units of fill, across
         * {@code orders}. Per-order amounts aren't kept — {@link FillInference} only nets
         * the total and steers a residual shortfall away from the orders.
         */
        public record Attribution(long volume, Set<UUID> orders) {
            static final Attribution NONE = new Attribution(0, Set.of());

            /** Whether any of this level's attributed volume went to {@code order}. */
            public boolean includes(UUID order) {
                return orders.contains(order);
            }

            Attribution merge(Attribution next) {
                var both = new HashSet<>(orders);
                both.addAll(next.orders);

                return new Attribution(volume + next.volume, Set.copyOf(both));
            }
        }

        static AttributedFills of(PriceType type, double price, UUID order, long amount) {
            return amount <= 0
                    ? NONE
                    : new AttributedFills(Map.of(new PriceGroup(type, price), new Attribution(amount, Set.of(order))));
        }

        /** What this mutation attributed at {@code group}'s level; {@link Attribution#NONE} if nothing. */
        public Attribution at(PriceGroup group) {
            return byLevel.getOrDefault(group, Attribution.NONE);
        }

        AttributedFills merge(AttributedFills next) {
            if (next.byLevel().isEmpty()) return this;
            if (byLevel.isEmpty()) return next;

            var merged = new HashMap<>(byLevel);
            next.byLevel().forEach((group, attribution) -> merged.merge(group, attribution, Attribution::merge));

            return new AttributedFills(Map.copyOf(merged));
        }
    }
}