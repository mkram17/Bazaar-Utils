package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataQuery;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A per-unit Bazaar price reading paired with its provenance precision — either
 * {@link Exact} (read directly from an item stack, confirmation screen, sign input, or
 * claim message; zero tolerance) or {@link Implied} (back-calculated from a displayed
 * notional total, which Hypixel rounds to whole coins at or above
 * {@link #FOLDING_THRESHOLD}).
 *
 * <p>Also serves as the static entry point for Hypixel's placement price bounds
 * ({@link #MINIMUM_PRICE}, {@link #minimumBid}, {@link #maximumAsk}) and live market
 * queries routed through {@link BazaarDataQuery}.
 */
public sealed interface PriceInfo permits PriceInfo.Exact, PriceInfo.Implied {
    /** Absolute game floor — {@code 0.0} is never a valid Bazaar order price. */
    double MINIMUM_PRICE = 0.1;

    /** Hypixel rejects buy bids below {@code best × MIN_BID_RATIO}. */
    double MIN_BID_RATIO = 2.0 / 3.0;

    /** Hypixel rejects sell asks above {@code best × MAX_ASK_RATIO}. */
    double MAX_ASK_RATIO = 3.0 / 2.0;

    /** Maximum coin error rounding a total to the nearest whole coin can introduce. Per-unit bound is {@code COIN_EPSILON / volume}. */
    double COIN_EPSILON = 0.5;

    /** Notional order value at or above which Hypixel rounds displayed coin totals to whole coins. */
    double FOLDING_THRESHOLD = 10_000.0;

    Codec<PriceInfo> CODEC = Codec.STRING.partialDispatch(
            "type",
            price -> DataResult.success(switch (price) {
                case Exact ignored -> "exact";
                case Implied ignored -> "implied";
            }),
            type -> switch (type) {
                case "exact" -> DataResult.success(Exact.CODEC);
                case "implied" -> DataResult.success(Implied.CODEC);
                default -> DataResult.error(() -> "Unknown PriceInfo type: " + type);
            }
    );

    /** Per-unit price in coins. */
    double pricePerItem();

    /** Per-unit tolerance band ({@code 0.0} for {@link Exact} and below-fold {@link Implied}). */
    double tolerance();

    /** {@code true} when {@link #tolerance()} is zero — this reading's price is exact. */
    default boolean isExact() {
        return tolerance() == 0.0;
    }

    /**
     * Exact decimal price reading — zero tolerance. Used for item-stack {@code Price per unit:}
     * lines, confirmation screens, direct sign input, and claim messages.
     */
    record Exact(double pricePerItem) implements PriceInfo {
        static final MapCodec<Exact> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.DOUBLE.fieldOf("price").forGetter(Exact::pricePerItem))
                        .apply(instance, Exact::new));

        public Exact {
            if (!Double.isFinite(pricePerItem) || pricePerItem < 0.0) {
                throw new IllegalArgumentException("Exact price must be finite and non-negative: " + pricePerItem);
            }
        }

        @Override
        public double tolerance() {
            return 0.0;
        }

        @Override
        public boolean isExact() {
            return true;
        }
    }

    /**
     * Per-unit price back-calculated from a displayed total across {@code volume} units
     * (chat order totals, Instant Sell / Sell Sacks lore). Tolerance is zero when the
     * notional total sits safely below {@link #FOLDING_THRESHOLD}; otherwise
     * {@code COIN_EPSILON / volume}.
     */
    record Implied(double pricePerItem, int volume) implements PriceInfo {
        static final MapCodec<Implied> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("price").forGetter(Implied::pricePerItem),
                        Codec.INT.fieldOf("volume").forGetter(Implied::volume)
                ).apply(instance, Implied::new));

        public Implied {
            if (!Double.isFinite(pricePerItem) || pricePerItem < 0.0) {
                throw new IllegalArgumentException("Implied price must be finite and non-negative: " + pricePerItem);
            }
        }

        @Override
        public double tolerance() {
            return computeTolerance(pricePerItem, volume);
        }
    }

    /** Returns an {@link Exact} reading truncated to one decimal place. */
    static Exact exact(double pricePerItem) {
        if (!Double.isFinite(pricePerItem) || pricePerItem < 0.0) {
            throw new IllegalArgumentException("Price must be finite and non-negative: " + pricePerItem);
        }
        return new Exact(Util.truncateNum(pricePerItem));
    }

    /** Returns an {@link Implied} reading truncated to one decimal place. */
    static Implied implied(double pricePerItem, int volume) {
        if (!Double.isFinite(pricePerItem) || pricePerItem < 0.0) {
            throw new IllegalArgumentException("Price must be finite and non-negative: " + pricePerItem);
        }
        return new Implied(Util.truncateNum(pricePerItem), volume);
    }

    /** Returns an {@link Exact} or {@link Implied} reading depending on {@code exact}. */
    static PriceInfo of(double pricePerItem, int volume, boolean exact) {
        return exact ? exact(pricePerItem) : implied(pricePerItem, volume);
    }

    /**
     * Computes the per-unit tolerance for a total-derived price and volume. Zero when
     * safely below {@link #FOLDING_THRESHOLD} (accounting for the rounding cliff where
     * a total just above {@code 10,000} rounds down below {@code 10,000}); otherwise
     * {@link #COIN_EPSILON} divided across {@code volume}. A non-positive price or volume
     * is treated as maximally uncertain — the full, undivided {@code COIN_EPSILON}.
     */
    static double computeTolerance(double price, int volume) {
        if (!Double.isFinite(price) || volume <= 0 || price <= 0.0) return COIN_EPSILON;
        if (price * volume + COIN_EPSILON < FOLDING_THRESHOLD) return 0.0;

        return COIN_EPSILON / volume;
    }

    /**
     * Symmetric comparison against another {@link PriceInfo}: exact IEEE equality when both
     * sides are exact, or combined tolerance bands plus symmetric ULP guard otherwise.
     */
    default boolean matches(@NotNull PriceInfo other) {
        if (this.isExact() && other.isExact()) {
            return Double.compare(this.pricePerItem(), other.pricePerItem()) == 0;
        }

        double combined = this.tolerance() + other.tolerance()
                + Math.max(Math.ulp(this.pricePerItem()), Math.ulp(other.pricePerItem()));

        return Util.genericIsSimilarValue(this.pricePerItem(), other.pricePerItem(), combined);
    }

    /** Compares against an exact scalar price. */
    default boolean matches(double exactPrice) {
        if (this.isExact()) {
            return Double.compare(this.pricePerItem(), exactPrice) == 0;
        }

        double combined = this.tolerance() + Math.max(Math.ulp(this.pricePerItem()), Math.ulp(exactPrice));

        return Util.genericIsSimilarValue(this.pricePerItem(), exactPrice, combined);
    }

    // ── Hypixel placement bounds ─────────────────────────────────────────────

    /** The lowest buy-order price Hypixel will accept given the current best bid. */
    static double minimumBid(double market) {
        if (!Double.isFinite(market) || market <= 0.0) return MINIMUM_PRICE;

        double floored = BigDecimal.valueOf(market)
                .multiply(BigDecimal.valueOf(2))
                .divide(BigDecimal.valueOf(3), 1, RoundingMode.CEILING)
                .doubleValue();

        return Math.max(MINIMUM_PRICE, floored);
    }

    /** The highest sell-offer price Hypixel will accept given the current best ask. */
    static double maximumAsk(double market) {
        if (!Double.isFinite(market) || market <= 0.0) return MINIMUM_PRICE;

        double ceiled = BigDecimal.valueOf(market)
                .multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.FLOOR)
                .doubleValue();

        return Math.max(MINIMUM_PRICE, ceiled);
    }

    // ── Live market query facade ─────────────────────────────────────────────

    /**
     * Returns the top-of-book price for {@code productId} on this side, or
     * empty if no tradable data exists. Delegates entirely to
     * {@link BazaarDataQuery#bestPrice(String, TransactionType)}.
     */
    static @NotNull OptionalDouble marketPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        return BazaarDataQuery.bestPrice(productId, transaction);
    }

    /**
     * Returns the open order count at {@code pricePerUnit}, or empty if
     * the level is absent or carries no live volume. Delegates to
     * {@link BazaarDataQuery#orderCount}.
     */
    static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return BazaarDataQuery.orderCount(productId, transaction, pricePerUnit);
    }

    /**
     * Returns the total volume at {@code pricePerUnit}, or empty if the
     * level is absent or carries no live volume. Delegates to
     * {@link BazaarDataQuery#totalVolume}.
     */
    static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return BazaarDataQuery.totalVolume(productId, transaction, pricePerUnit);
    }

    /**
     * Calculates the price required to achieve this position relative to
     * the current top of book.
     *
     * @param productId   the resolved Bazaar product ID
     * @param transaction the transaction type and method context
     * @return the calculated price, or empty if the product is unknown or has no tradable data
     *
     * @see PricingPosition#adjust(double, TransactionType)
     */
    static OptionalDouble priceForPosition(@Nullable String productId, @NotNull TransactionType transaction, @NotNull PricingPosition position) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalDouble.empty();
        }

        OptionalDouble marketOpt = marketPrice(productId, transaction);
        if (marketOpt.isEmpty()) return OptionalDouble.empty();

        double market = marketOpt.getAsDouble();

        return OptionalDouble.of(position.adjust(market, transaction));
    }

    /**
     * Self-outbid-aware variant of {@link #priceForPosition(String, TransactionType, PricingPosition)}.
     *
     * <p>When {@code selfOutbid} is {@code false} and this position is
     * {@link PricingPosition#COMPETITIVE}, this method first checks whether the player
     * already occupies the top of the book. If so, it returns the current
     * market price unchanged rather than stepping above it—outbidding one's
     * own order costs coins for no gain in queue position.
     *
     * <p>{@link PricingPosition#MATCHED} and {@link PricingPosition#OUTBID} are unaffected: MATCHED is
     * already idempotent at the market price, and OUTBID is a deliberate
     * step back that self-ownership has no bearing on.
     *
     * @param productId   the resolved Bazaar product ID
     * @param transaction the transaction type and method context
     * @param userOrders  snapshot of the player's tracked orders
     * @param selfOutbid  when {@code true}, the player's own top-of-book orders count
     *                    as ordinary competition, and COMPETITIVE steps above them
     *
     * @return the calculated price, or empty if the product is unknown or has no tradable data
     */
    static OptionalDouble priceForPosition(@NotNull String productId, @NotNull TransactionType transaction, @NotNull PricingPosition position, @NotNull List<Order> userOrders, boolean selfOutbid) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalDouble.empty();
        }

        OptionalDouble marketOpt = BazaarDataQuery.bestPrice(productId, transaction);
        if (marketOpt.isEmpty()) return OptionalDouble.empty();

        double market = marketOpt.getAsDouble();

        if (!selfOutbid && position == PricingPosition.COMPETITIVE && transaction.isOrder()) {
            long selfOrderCount = userOrders.stream()
                    .filter(Order.forProduct(productId, transaction.getSide()))
                    .filter(Order::isOpen)
                    .filter(Order.forExactPrice(market))
                    .count();

            OptionalInt orderCountOpt = orderCount(productId, transaction, market);

            if (selfOrderCount > 0 && orderCountOpt.isPresent() && selfOrderCount >= orderCountOpt.getAsInt()) {
                return OptionalDouble.of(market);
            }
        }

        return OptionalDouble.of(position.adjust(market, transaction));
    }

    /**
     * Evaluates the competitive standing of a specific price against the current book.
     *
     * @param productId    the resolved Bazaar product ID
     * @param transaction  the transaction type and method context
     * @param pricePerUnit the price to evaluate
     * @return {@link PricingPosition#OUTBID} if at least one order sits ahead of the price,
     *         {@link PricingPosition#MATCHED} if it ties the best price alongside other orders, or
     *         {@link PricingPosition#COMPETITIVE} if it alone holds the best price (or beats all resting levels). Empty if no data exists.
     */
    static Optional<PricingPosition> position(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        var aheadOpt = BazaarDataQuery.positionOf(productId, transaction, pricePerUnit);
        if (aheadOpt.isEmpty()) return Optional.empty();

        int ahead = aheadOpt.getAsInt();
        if (ahead > 0) return Optional.of(PricingPosition.OUTBID);

        var orderCountOpt = orderCount(productId, transaction, pricePerUnit);
        if (orderCountOpt.isEmpty()) return Optional.of(PricingPosition.COMPETITIVE);

        return Optional.of(PricingPosition.of(ahead, orderCountOpt.getAsInt(), 0, true));
    }
}
