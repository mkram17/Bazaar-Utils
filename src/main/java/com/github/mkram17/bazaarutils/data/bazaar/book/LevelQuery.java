package com.github.mkram17.bazaarutils.data.bazaar.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A single-pass, push-based accumulation over a sequence of {@link LevelReconciliation}
 * values, without ever materializing the sequence itself into a collection.
 *
 * <p>A caller pushes one level at a time via {@link #push}; a {@code false} return means
 * the query has everything it needs and no further levels should be pushed. Once
 * pushing is done, {@link #result()} extracts the answer.
 *
 * <p>The static factories are the building blocks; {@link #andThen} and {@link #teeing}
 * compose them.
 */
public interface LevelQuery<R> {
    /**
     * Accepts one more level. Returns {@code true} to keep receiving levels,
     * {@code false} once this query has everything it needs.
     */
    boolean push(LevelReconciliation level);

    /** The accumulated result, valid to call at any point but meaningful once pushing has stopped. */
    R result();

    /**
     * Captures the value {@code extractor} derives from the first pushed level
     * where it yields one, then stops. {@link #filtering} can only decide
     * whether the original level is forwarded — a caller that also wants a
     * value out of it has to re-derive whatever the predicate already computed.
     * This calls {@code extractor} exactly once per level and forwards the
     * unwrapped value it found, not the level itself.
     */
    static <V> LevelQuery<Optional<V>> first(Function<? super LevelReconciliation, Optional<V>> extractor) {
        return new LevelQuery<>() {
            V found;

            @Override
            public boolean push(LevelReconciliation level) {
                Optional<V> extracted = extractor.apply(level);
                extracted.ifPresent(v -> found = v);

                return extracted.isEmpty();
            }

            @Override
            public Optional<V> result() {
                return Optional.ofNullable(found);
            }
        };
    }

    /**
     * Passes only levels matching {@code predicate} through to {@code downstream}; a
     * non-matching level is silently skipped without affecting it.
     */
    static <R> LevelQuery<R> filtering(Predicate<? super LevelReconciliation> predicate, LevelQuery<R> downstream) {
        return new LevelQuery<>() {
            @Override
            public boolean push(LevelReconciliation level) {
                return !predicate.test(level) || downstream.push(level);
            }

            @Override
            public R result() {
                return downstream.result();
            }
        };
    }

    /**
     * Collects levels at positions {@code from} through {@code to}, inclusive on both
     * ends, counting the first pushed level as position 1 — not 0.
     */
    static LevelQuery<List<LevelReconciliation>> range(int from, int to) {
        return new LevelQuery<>() {
            int index = 0;

            final List<LevelReconciliation> collected = new ArrayList<>();

            @Override
            public boolean push(LevelReconciliation level) {
                index++;

                if (index < from) return true;
                if (index <= to) collected.add(level);

                return index < to;
            }

            @Override
            public List<LevelReconciliation> result() {
                return collected;
            }
        };
    }

    /**
     * Forwards levels to {@code downstream} only while {@code predicate} holds; stops
     * permanently the first time it fails, even if a later level would have passed.
     */
    static <R> LevelQuery<R> takingWhile(Predicate<? super LevelReconciliation> predicate, LevelQuery<R> downstream) {
        return new LevelQuery<>() {
            @Override
            public boolean push(LevelReconciliation level) {
                return predicate.test(level) && downstream.push(level);
            }

            @Override
            public R result() {
                return downstream.result();
            }
        };
    }

    /** Counts every pushed level; never stops early. */
    static LevelQuery<Long> counting() {
        return new LevelQuery<>() {
            long count = 0;

            @Override
            public boolean push(LevelReconciliation level) {
                count++;

                return true;
            }

            @Override
            public Long result() {
                return count;
            }
        };
    }

    /** Captures only the first pushed level, then stops. Empty if nothing was ever pushed. */
    static LevelQuery<Optional<LevelReconciliation>> reconciliation() {
        return first(Optional::of);
    }

    /** Returns a query with the same push behavior, transforming only the final result through {@code finisher}. */
    default <V> LevelQuery<V> andThen(Function<R, V> finisher) {
        LevelQuery<R> self = this;

        return new LevelQuery<>() {
            @Override
            public boolean push(LevelReconciliation level) {
                return self.push(level);
            }

            @Override
            public V result() {
                return finisher.apply(self.result());
            }
        };
    }

    /**
     * Runs {@code first} and {@code second} against the same sequence in a single pass,
     * merging their two results once both are done. Each stops independently — once one
     * signals it's done, only the other keeps receiving levels — and the combined query
     * itself stops once both have.
     */
    static <A, B, R> LevelQuery<R> teeing(LevelQuery<A> first, LevelQuery<B> second, BiFunction<A, B, R> merger) {
        return new LevelQuery<>() {
            boolean firstDone, secondDone;

            @Override
            public boolean push(LevelReconciliation level) {
                if (!firstDone) firstDone = !first.push(level);
                if (!secondDone) secondDone = !second.push(level);

                return !(firstDone && secondDone);
            }

            @Override
            public R result() {
                return merger.apply(first.result(), second.result());
            }
        };
    }
}
