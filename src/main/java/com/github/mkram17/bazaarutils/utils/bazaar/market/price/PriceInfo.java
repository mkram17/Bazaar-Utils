package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
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
 * Immutable value object pairing a resolved {@code productInfo + transaction + pricePerUnit}.
 *
 *  <p>Serves two roles:
 *  <ol>
 *      <li><b>Value</b> — a snapshot of a specific pricePerUnit point for a known product.</li>
 *      <li><b>Query host</b> — static and instance methods for all market queries.</li>
 *  </ol>
 *  </p>
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
     * Creates market pricePerUnit metadata for a product snapshot.
     *
     * @param productId   resolved Bazaar product ID
     * @param transaction buy/sell + order/insta context
     * @param pricePerItem pricePerUnit per unit, truncated to one decimal place
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
     * Returns the best available pricePerUnit (top of book) for the given product and transaction.
     */
    public static @NotNull OptionalDouble marketPrice(@Nullable String productId, @NotNull TransactionType transaction) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalDouble.empty();
        }

        var data = BazaarDataRegistry.get(productId);
        if (data == null) return OptionalDouble.empty();

        var entry = data.bookFor(transaction).firstEntry();
        if (entry == null) return OptionalDouble.empty();

        return OptionalDouble.of(entry.getValue().pricePerUnit());
    }

    /**
     * Returns the number of projected orders at the given pricePerUnit level.
     */
    public static @NotNull OptionalInt orderCount(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalInt.empty();
        }

        var data = BazaarDataRegistry.get(productId);
        if (data == null) return OptionalInt.empty();

        var pool = data.bookFor(transaction).get(pricePerUnit);
        if (pool == null) return OptionalInt.empty();

        return OptionalInt.of(pool.orderCount());
    }

    public static @NotNull OptionalInt totalVolume(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return OptionalInt.empty();
        }

        var data = BazaarDataRegistry.get(productId);
        if (data == null) return OptionalInt.empty();

        var pool = data.bookFor(transaction).get(pricePerUnit);
        if (pool == null) return OptionalInt.empty();

        return OptionalInt.of((int) pool.totalVolume());
    }

    /**
     * Returns the adjusted pricePerUnit that would achieve {@code position}
     * relative to the current top of book.
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
     * UserOrders-aware variant of {@link #priceForPosition(String, TransactionType, PricingPosition)}.
     *
     * <p>When {@code selfOutbid} is {@code false} and the requested position is
     * {@link PricingPosition#COMPETITIVE}, checks whether the user already occupies the top
     * of book for this product/side. If so, returns the current market price directly rather
     * than adjusting above it — outbidding your own order costs coins for no queue gain.
     *
     * <p>MATCHED and OUTBID are unaffected: MATCHED is already idempotent, and OUTBID is a
     * deliberate step back that is not affected by self-ownership.
     *
     * @param selfOutbid when {@code true}, own top-of-book orders are treated as external
     *                   competitors and COMPETITIVE will step above them as normal
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
     * Determines whether the pricePerUnit is competitive, matched, or outbid relative to the market.
     *
     * @return status reflecting how this order compares to current prices.
     */
    public static Optional<PricingPosition> position(@Nullable String productId, @NotNull TransactionType transaction, double pricePerUnit) {
        if (!ProductInfo.isValidProductId(productId)) {
            Util.logMessage("Query skipped — invalid product ID: %s".formatted(productId));

            return Optional.empty();
        }

        var data = BazaarDataRegistry.get(productId);
        if (data == null) return Optional.empty();

        int ahead = data.positionOf(transaction, pricePerUnit);

        if (ahead > 0) return Optional.of(PricingPosition.OUTBID);

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
     * @see #orderCount(String, TransactionType, double)
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
