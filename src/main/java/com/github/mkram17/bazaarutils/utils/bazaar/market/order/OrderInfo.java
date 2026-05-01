package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Snapshot of one Bazaar order interaction: product display name, side, per-unit price, and volume.
 *
 * <p>Extends {@link PriceInfo} to inherit static market-query methods. The primary purpose beyond
 * being a data carrier is price-similarity matching via {@link #isPriceSimilarTo}: Hypixel rounds
 * displayed totals above {@link #FOLDING_THRESHOLD} (10 000 coins total order value), introducing
 * up to {@link #COIN_EPSILON} (0.9 coins) of per-unit error. {@link #computeTolerance} derives
 * the per-unit band from those constants and the order volume.
 */
@Getter
@ToString(callSuper=true)
public class OrderInfo extends PriceInfo {
    /**
     * Maximum per-unit coin inaccuracy Hypixel can introduce in displayed prices.
     */
    public static final double COIN_EPSILON = 0.9;

    /**
     * Total order value above which Hypixel starts rounding displayed prices/whereof the epsilon is to be consumed.
     */
    public static final double FOLDING_THRESHOLD = 10000;

    @NotNull
    private final String name;

    private final int volume;

    private final double tolerance;

    /**
     * @param name         item display name
     * @param productId    resolved Bazaar product identifier
     * @param side         order side
     * @param pricePerItem price per unit; truncated to one decimal place by {@link PriceInfo}
     * @param volume       order quantity; determines the per-unit tolerance band
     */
    private OrderInfo(
            @NotNull String name,
            @NotNull String productId,
            @NotNull TransactionType.Side side,
            @NotNull Double pricePerItem,
            @NotNull Integer volume) {
        super(productId, TransactionType.of(side, TransactionType.Method.ORDER), pricePerItem);
        this.name = name;
        this.volume = volume;
        this.tolerance = computeTolerance(pricePerItem, volume);
    }

    /**
     * Resolves {@code name} to a product ID via {@link ProductInfo#fromDisplayName} and
     * constructs an instance. Returns empty when the name is unknown.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer)
     */
    public static Optional<OrderInfo> of(@NotNull String name, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume) {
        return ProductInfo.fromDisplayName(name).map(info -> new OrderInfo(name, info.getProductId(), side, pricePerItem, volume));
    }

    /**
     * Constructs directly from a known product ID, bypassing name resolution.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer)
     */
    public static OrderInfo of(@NotNull String name, @NotNull String productId, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume) {
        return new OrderInfo(name, productId, side, pricePerItem, volume);
    }

    public static Optional<OrderInfo> of(@NotNull Order order) {
        return ProductInfo.fromProductId(order.productId())
                .map(info -> new OrderInfo(info.getName(), order.productId(), order.side(), order.pricePerItem(), order.originalAmount()));
    }

    /**
     * Returns {@code true} when {@code other} is within the tolerance band for this order.
     *
     * <p>Tolerance = {@link #tolerance} + 1% of {@code other} to absorb floating-point residuals.
     */
    public boolean isPriceSimilarTo(double other) {
        return Util.genericIsSimilarValue(getPricePerItem(), other, tolerance + other * 0.01);
    }

    /**
     * Computes the per-unit pricePerUnit tolerance for this order.
     *
     * <p>When the total order value is below {@link #FOLDING_THRESHOLD}, Hypixel does not
     * round, so tolerance is 0. Above the threshold, up to {@link #COIN_EPSILON} coins
     * of rounding can appear in the total, which translates to a per-unit tolerance of
     * {@code round(COIN_ROUNDING_CAP / volume, 1)}.
     */
    public static double computeTolerance(double price, int volume) {
        if (volume <= 0 || price <= 0) return COIN_EPSILON;
        if (price * volume < FOLDING_THRESHOLD) return 0.0;
        return Math.round((COIN_EPSILON / volume) * 10) / 10.0;
    }
}
