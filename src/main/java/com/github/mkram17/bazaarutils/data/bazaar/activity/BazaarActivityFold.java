package com.github.mkram17.bazaarutils.data.bazaar.activity;

import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;

import java.util.List;
import java.util.function.*;
import java.util.stream.*;

/**
 * A reusable, declarable reduction over a stream of {@link BazaarActivityRecord}s.
 *
 * <p>Declare instances as {@code static final} constants and pass them to
 * {@link BazaarActivityStorage#fold} or {@link BazaarActivityStorage#foldToday} —
 * the same way you'd declare a {@link java.util.Comparator}. The constant describes
 * <em>how</em> to reduce; the data it runs over is always live at call time.
 *
 * <p>Factory methods mirror {@link java.util.stream.Collectors}: {@link #filtering}
 * wraps a downstream fold with a pre-filter; {@link #teeing} fans out to two
 * independent folds over the same data in a single pass.
 *
 * <h2>teeing and stream materialization</h2>
 * {@link #teeing} materializes the stream into a {@link java.util.List} before
 * dispatching to both child folds. This is necessary because streams are single-use.
 * Avoid nesting {@code teeing} calls — the inner materialization compounds.
 *
 * <h2>andThen</h2>
 * {@link #andThen} applies a post-processing function to the fold result without
 * re-streaming. Use it to attach export-state filtering to a static fold constant
 * at call time, rather than baking dynamic state into the constant itself.
 */
@FunctionalInterface
public interface BazaarActivityFold<R> {

    R fold(Stream<BazaarActivityRecord> records);

    /**
     * Pre-filters records before passing to {@code downstream}.
     */
    static <R> BazaarActivityFold<R> filtering(Predicate<? super BazaarActivityRecord> predicate, BazaarActivityFold<R> downstream) {
        return records -> downstream.fold(records.filter(predicate));
    }

    /**
     * Fans out to two independent folds over the same stream, merging results.
     */
    static <A, B, R> BazaarActivityFold<R> teeing(BazaarActivityFold<A> first, BazaarActivityFold<B> second, BiFunction<A, B, R> merger) {
        return records -> {
            List<BazaarActivityRecord> materialized = records.toList();

            A a = first.fold(materialized.stream());
            B b = second.fold(materialized.stream());

            return merger.apply(a, b);
        };
    }

    static BazaarActivityFold<Double> summingDouble(ToDoubleFunction<? super BazaarActivityRecord> mapper) {
        return records -> records.mapToDouble(mapper).sum();
    }

    static BazaarActivityFold<Long> counting() {
        return records -> records.mapToLong(r -> 1L).sum();
    }

    static <R> BazaarActivityFold<R> collecting(Collector<BazaarActivityRecord, ?, R> collector) {
        return records -> records.collect(collector);
    }

    /**
     * Transforms the result of this fold after it is computed.
     */
    default <V> BazaarActivityFold<V> andThen(Function<R, V> finisher) {
        return records -> finisher.apply(fold(records));
    }
}