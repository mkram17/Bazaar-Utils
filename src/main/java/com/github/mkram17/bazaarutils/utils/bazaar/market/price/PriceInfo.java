package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.data.bazaar.MarketQuery;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immutable snapshot of a product at one price and transaction type.
 *
 * <p>Also the static entry point most of the mod uses for live market
 * queries, routed through
 * {@link com.github.mkram17.bazaarutils.data.bazaar.MarketQuery} so this
 * class never has to know about the book's own base/overlay split.
 * Instance methods delegate to their static equivalents using this
 * object's own fields.
 *
 * <p>Price constants ({@link #MINIMUM_PRICE}, {@link #MIN_BID_RATIO},
 * {@link #MAX_ASK_RATIO}) reflect Hypixel's enforced order-placement
 * bounds.
 */
@Getter
@ToString
public class PriceInfo implements ProductInfo {
    /** Absolute game floor — {@code 0.0} is never a valid Bazaar order price. */
    public static final double MINIMUM_PRICE = 0.1;

    /** Hypixel rejects buy bids below {@code truncateNum(best × MIN_BID_RATIO)}. */
    public static final double MIN_BID_RATIO = 2.0 / 3.0;

    /** The lowest buy-order price Hypixel will accept given the current best bid. */
    public static double minimumBid(double market) {
        return Util.truncateNum(market * MIN_BID_RATIO);
    }

    /** Hypixel rejects sell asks above {@code truncateNum(best × MAX_ASK_RATIO)}. */
    public static final double MAX_ASK_RATIO = 3.0 / 2.0;


    /** The highest sell-offer price Hypixel will accept given the current best ask. */
    public static double maximumAsk(double market) {
        return Util.truncateNum(market * MAX_ASK_RATIO);
    }

    @NotNull
    private final String productId;

    @NotNull
    private final TransactionType transaction;

    @NotNull
    private final Double pricePerItem;

    /**
     * @param productId    resolved Bazaar product ID
     * @param transaction  buy/sell and method context
     * @param pricePerItem price per unit; truncated to one decimal place on construction
     */
    public PriceInfo(
            @NotNull String productId,
            @NotNull TransactionType transaction,
            @NotNull Double pricePerItem) {
        this.productId = productId;
        this.transaction = transaction;
        this.pricePerItem = Util.truncateNum(pricePerItem);
    }

    /**
     * Returns the top-of-book price for {@code productId} on this side, or
     * empty if no tradable data exists. Delegates entirely to
     * {@link MarketQuery#bestPrice}.
     */
    public static @NotNull OptionalDouble marketPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        return MarketQuery.bestPrice(productId, transaction);
    }

    /**
     * Returns the open order count at {@code pricePerUnit}, or empty if
     * the level is absent or carries no live volume. Delegates to
     * {@link MarketQuery#orderCount}.
     */
    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return MarketQuery.orderCount(productId, transaction, pricePerUnit);
    }

    /**
     * Returns the total volume at {@code pricePerUnit}, or empty if the
     * level is absent or carries no live volume. Delegates to
     * {@link MarketQuery#totalVolume}.
     */
    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        return MarketQuery.totalVolume(productId, transaction, pricePerUnit);
    }

    /**
     * Returns the price that would achieve {@code position} relative to
     * the current top of book, or empty if the product is unknown or has
     * no tradable data on this side.
     *
     * @see PricingPosition#adjust(double, TransactionType)
     */
    public static OptionalDouble priceForPosition(@Nullable String productId, @NotNull TransactionType transaction, @NotNull PricingPosition position) {
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
     * Self-outbid-aware variant of
     * {@link #priceForPosition(String, TransactionType, PricingPosition)}.
     *
     * <p>When {@code selfOutbid} is {@code false} and {@code position} is
     * {@link PricingPosition#COMPETITIVE}, first checks whether the player
     * already occupies the top of book on this side. If so, returns the
     * current market price unchanged rather than stepping above it —
     * outbidding one's own order costs coins for no gain in queue position.
     *
     * <p>{@link PricingPosition#MATCHED} and {@link PricingPosition#OUTBID}
     * are unaffected: MATCHED is already idempotent at the market price,
     * and OUTBID is a deliberate step back that self-ownership has no
     * bearing on.
     *
     * @param selfOutbid when {@code true}, the player's own top-of-book
     *                   orders count as ordinary competition and
     *                   COMPETITIVE steps above them as usual
     */
    public static OptionalDouble priceForPosition(@Nullable String productId, @NotNull TransactionType transaction, @NotNull PricingPosition position, @NotNull List<Order> userOrders, boolean selfOutbid) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalDouble.empty();
        }

        OptionalDouble marketOpt = marketPrice(productId, transaction);
        if (marketOpt.isEmpty()) return OptionalDouble.empty();

        double market = marketOpt.getAsDouble();

        if (!selfOutbid && position == PricingPosition.COMPETITIVE) {
            boolean selfAtTop = userOrders.stream()
                    .filter(Order.forProduct(productId, transaction.getSide()))
                    .filter(Order::isActive)
                    .anyMatch(order -> order.pricePerItem() == market);

            if (selfAtTop) return OptionalDouble.of(market);
        }

        return OptionalDouble.of(position.adjust(market, transaction));
    }

    /**
     * Returns the competitive standing of {@code pricePerUnit} against the
     * current book: {@link PricingPosition#OUTBID} when at least one order
     * sits ahead of it, {@link PricingPosition#MATCHED} when it ties the
     * best price alongside other orders, {@link PricingPosition#COMPETITIVE}
     * when it alone holds the best price. Empty when the product has no
     * data at this price.
     */
    public static Optional<PricingPosition> position(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        var aheadOpt = MarketQuery.positionOf(productId, transaction, pricePerUnit);
        if (aheadOpt.isEmpty()) return Optional.empty();

        if (aheadOpt.getAsInt() > 0) return Optional.of(PricingPosition.OUTBID);

        var orderCountOpt = orderCount(productId, transaction, pricePerUnit);
        if (orderCountOpt.isEmpty()) return Optional.empty();

        return Optional.of(orderCountOpt.getAsInt() > 1 ? PricingPosition.MATCHED : PricingPosition.COMPETITIVE);
    }

    /**
     * @see #marketPrice(String, TransactionType)
     */
    public OptionalDouble marketPrice(@NotNull TransactionType.Side side) {
        return marketPrice(getProductId(), TransactionType.of(side, TransactionType.Method.ORDER));
    }

    /**
     * @see #marketPrice(String, TransactionType)
     */
    public OptionalDouble marketPrice() {
        return marketPrice(getProductId(), getTransaction());
    }

    /**
     * @see #orderCount(String, TransactionType, double)
     */
    public OptionalInt orderCount() {
        return orderCount(getProductId(), getTransaction(), getPricePerItem());
    }

    /**
     * @see #totalVolume(String, TransactionType, double)
     */
    public OptionalInt totalVolume() {
        return totalVolume(getProductId(), getTransaction(), getPricePerItem());
    }

    /**
     * @see #priceForPosition(String, TransactionType, PricingPosition)
     */
    public OptionalDouble priceForPosition(PricingPosition position) {
        return priceForPosition(getProductId(), getTransaction(), position);
    }

    /**
     * @see #position(String, TransactionType, double)
     */
    public Optional<PricingPosition> position() {
        return position(getProductId(), getTransaction(), getPricePerItem());
    }
}
