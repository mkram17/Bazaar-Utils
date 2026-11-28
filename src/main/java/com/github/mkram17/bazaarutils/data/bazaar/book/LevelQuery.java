package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * <p>Structural combinators ({@link #range}, {@link #top}, {@link #reconciliation},
 * {@link #filtering}, {@link #takingWhile}) observe every reconciled level — vacated
 * overlay tombstones included — while market-microstructure combinators and accumulators
 * ({@link #tradable}, {@link #withinPercentOfBest}, {@link #upToPrice},
 * {@link #totalVolume}, {@link #totalValue}, {@link #totalOrders}, {@link #execution})
 * operate strictly on liquid prevailing levels for depth, slippage, and manipulation
 * guards. {@link #andThen} and {@link #teeing} compose any combination in one O(N) pass.
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
     * Result of simulating execution of a target unit quantity across one side of the book
     * (via {@link #execution(long)}).
     */
    record ExecutionSummary(
            long filledVolume,
            long unfilledVolume,
            double totalCoins,
            double bestPrice,
            double worstPrice,
            int levelsConsumed
    ) {
        public static final ExecutionSummary EMPTY = new ExecutionSummary(0L, 0L, 0.0, 0.0, 0.0, 0);

        /** {@code true} when every requested unit was matched against resting liquidity. */
        public boolean isFullyFilled() {
            return unfilledVolume == 0L && filledVolume > 0L;
        }

        /** Volume-weighted average execution price across all consumed levels. */
        public double vwap() {
            return filledVolume > 0L ? totalCoins / filledVolume : 0.0;
        }

        /** Relative price impact from {@code bestPrice} to {@link #vwap()} (e.g. {@code 0.05} = 5% slippage). */
        public double slippage() {
            return bestPrice > 0.0 && filledVolume > 0L
                    ? Math.abs(vwap() - bestPrice) / bestPrice
                    : 0.0;
        }

        /** Relative tail impact from {@code bestPrice} to the {@code worstPrice} touched by the walk. */
        public double worstImpact() {
            return bestPrice > 0.0 && filledVolume > 0L
                    ? Math.abs(worstPrice - bestPrice) / bestPrice
                    : 0.0;
        }
    }

    // ── Core structural combinators ───────────────────────────────────────────

    /**
     * Captures the value {@code extractor} derives from the first pushed level
     * where it yields one, then latches permanently and stops.
     */
    static <V> LevelQuery<Optional<V>> first(Function<? super LevelReconciliation, Optional<V>> extractor) {
        Objects.requireNonNull(extractor, "extractor");

        return new LevelQuery<>() {
            V found;

            @Override
            public boolean push(LevelReconciliation level) {
                if (found != null) return false;

                Optional<V> extracted = extractor.apply(level);
                extracted.ifPresent(v -> found = v);

                return found == null;
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
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(downstream, "downstream");

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
     * Passes only levels with live prevailing liquidity ({@link LevelReconciliation#hasLiquidity()})
     * through to {@code downstream}, ignoring vacated overlay tombstones.
     */
    static <R> LevelQuery<R> tradable(LevelQuery<R> downstream) {
        return filtering(LevelReconciliation::hasLiquidity, downstream);
    }

    /**
     * Collects levels at positions {@code from} through {@code to}, inclusive on both
     * ends, counting the first pushed level as position 1 — not 0.
     */
    static LevelQuery<List<LevelReconciliation>> range(int from, int to) {
        if (from < 1 || to < from) {
            throw new IllegalArgumentException("Invalid level range: %d..%d (requires 1 <= from <= to)".formatted(from, to));
        }

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
                return List.copyOf(collected);
            }
        };
    }

    /** Collects the first {@code n} pushed levels (positions {@code 1..n}). */
    static LevelQuery<List<LevelReconciliation>> top(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Level count must be at least 1: " + n);
        }

        return range(1, n);
    }

    /**
     * Forwards levels to {@code downstream} only while {@code predicate} holds; latches
     * permanently on the first failure, even if a later level would have passed.
     */
    static <R> LevelQuery<R> takingWhile(Predicate<? super LevelReconciliation> predicate, LevelQuery<R> downstream) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(downstream, "downstream");

        return new LevelQuery<>() {
            boolean stopped = false;

            @Override
            public boolean push(LevelReconciliation level) {
                if (stopped) return false;
                if (!predicate.test(level)) {
                    stopped = true;
                    return false;
                }

                if (!downstream.push(level)) {
                    stopped = true;
                    return false;
                }

                return true;
            }

            @Override
            public R result() {
                return downstream.result();
            }
        };
    }

    /** General left-fold over pushed levels; never stops early. */
    static <R> LevelQuery<R> folding(R seed, BiFunction<R, ? super LevelReconciliation, R> accumulator) {
        Objects.requireNonNull(accumulator, "accumulator");

        return new LevelQuery<>() {
            R state = seed;

            @Override
            public boolean push(LevelReconciliation level) {
                state = accumulator.apply(state, level);
                return true;
            }

            @Override
            public R result() {
                return state;
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

    // ── Market-microstructure windows & accumulators ──────────────────────────

    /**
     * Anchors on the first liquid level's price ({@code bestPrice}) and forwards liquid
     * levels to {@code downstream} while their relative deviation from {@code bestPrice}
     * stays within {@code maxDeviation} (e.g. {@code 0.05} = within 5% of top-of-book).
     * Stops permanently once a liquid level falls outside the band.
     */
    static <R> LevelQuery<R> withinPercentOfBest(double maxDeviation, LevelQuery<R> downstream) {
        if (!Double.isFinite(maxDeviation) || maxDeviation < 0.0) {
            throw new IllegalArgumentException("maxDeviation must be finite and non-negative: " + maxDeviation);
        }
        Objects.requireNonNull(downstream, "downstream");

        return new LevelQuery<>() {
            double bestPrice = Double.NaN;
            boolean stopped = false;

            @Override
            public boolean push(LevelReconciliation level) {
                if (stopped) return false;
                if (!level.hasLiquidity()) return true;

                double price = level.pricePerUnit();
                if (Double.isNaN(bestPrice)) {
                    bestPrice = price;
                } else {
                    boolean inBand = bestPrice > 0.0
                            ? Math.abs(price - bestPrice) / bestPrice <= maxDeviation
                            : Double.compare(price, bestPrice) == 0;
                    if (!inBand) {
                        stopped = true;
                        return false;
                    }
                }

                if (!downstream.push(level)) {
                    stopped = true;
                    return false;
                }

                return true;
            }

            @Override
            public R result() {
                return downstream.result();
            }
        };
    }

    /**
     * Forwards levels to {@code downstream} while their price is at least as good as
     * {@code priceLimit} under {@code type}'s direction, stopping permanently once the
     * limit is crossed.
     */
    static <R> LevelQuery<R> upToPrice(@NotNull PriceType type, double priceLimit, LevelQuery<R> downstream) {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(priceLimit)) {
            throw new IllegalArgumentException("priceLimit must be finite: " + priceLimit);
        }

        return takingWhile(level -> type.atLeastAsGood(level.pricePerUnit(), priceLimit), downstream);
    }

    /** Cumulative tradable unit depth across pushed liquid levels. */
    static LevelQuery<Long> totalVolume() {
        return new LevelQuery<>() {
            long volume = 0L;

            @Override
            public boolean push(LevelReconciliation level) {
                var tradable = level.tradableOrNull();
                if (tradable != null) {
                    volume += tradable.totalVolume();
                }

                return true;
            }

            @Override
            public Long result() {
                return volume;
            }
        };
    }

    /** Cumulative tradable coin/notional depth ({@code Σ price × volume}) across pushed liquid levels. */
    static LevelQuery<Double> totalValue() {
        return new LevelQuery<>() {
            double value = 0.0;

            @Override
            public boolean push(LevelReconciliation level) {
                var tradable = level.tradableOrNull();
                if (tradable != null) {
                    value += tradable.pricePerUnit() * tradable.totalVolume();
                }

                return true;
            }

            @Override
            public Double result() {
                return value;
            }
        };
    }

    /** Cumulative distinct order count across pushed liquid levels (for depth-breadth / whale checks). */
    static LevelQuery<Integer> totalOrders() {
        return new LevelQuery<>() {
            int orders = 0;

            @Override
            public boolean push(LevelReconciliation level) {
                var tradable = level.tradableOrNull();
                if (tradable != null) {
                    orders += tradable.orderCount();
                }

                return true;
            }

            @Override
            public Integer result() {
                return orders;
            }
        };
    }

    /**
     * Simulates walking liquid levels in book order to fill {@code targetVolume} units,
     * stopping as soon as {@code targetVolume} is satisfied.
     */
    static LevelQuery<ExecutionSummary> execution(long targetVolume) {
        if (targetVolume < 0L) {
            throw new IllegalArgumentException("targetVolume must be non-negative: " + targetVolume);
        }

        return new LevelQuery<>() {
            long remaining = targetVolume;
            long filled = 0L;
            double totalCoins = 0.0;
            double bestPrice = 0.0;
            double worstPrice = 0.0;
            int levelsConsumed = 0;

            @Override
            public boolean push(LevelReconciliation level) {
                if (remaining <= 0L) return false;

                var tradable = level.tradableOrNull();
                if (tradable == null) return true;

                long take = Math.min(remaining, tradable.totalVolume());
                if (levelsConsumed == 0) {
                    bestPrice = tradable.pricePerUnit();
                }
                worstPrice = tradable.pricePerUnit();
                filled += take;
                totalCoins += take * tradable.pricePerUnit();
                levelsConsumed++;
                remaining -= take;

                return remaining > 0L;
            }

            @Override
            public ExecutionSummary result() {
                if (targetVolume == 0L) return ExecutionSummary.EMPTY;

                return new ExecutionSummary(filled, remaining, totalCoins, bestPrice, worstPrice, levelsConsumed);
            }
        };
    }

    // ── Composition ───────────────────────────────────────────────────────────

    /** Returns a query with the same push behavior, transforming only the final result through {@code finisher}. */
    default <V> LevelQuery<V> andThen(Function<? super R, ? extends V> finisher) {
        Objects.requireNonNull(finisher, "finisher");
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
    static <A, B, R> LevelQuery<R> teeing(
            LevelQuery<A> first,
            LevelQuery<B> second,
            BiFunction<? super A, ? super B, ? extends R> merger) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(merger, "merger");

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
