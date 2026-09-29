package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceQuantity;
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
public class OrderInfo extends PriceInfo implements PriceQuantity {
    @NotNull
    private final String name;

    private final int volume;

    private final double tolerance;

    private final boolean priceExact;

    /**
     * @param name         item display name
     * @param productId    resolved Bazaar product identifier
     * @param side         order side
     * @param pricePerItem price per unit; truncated to one decimal place by {@link PriceInfo}
     * @param volume       order quantity; determines the per-unit tolerance band
     * @param priceExact   {@code true} when {@code pricePerItem} came from an authoritative
     *                     per-unit source (a confirmation screen or Orders page "Price per unit:"
     *                     line); {@code false} when it was derived by dividing a displayed total
     *                     by {@code volume} (Instant Sell / Sell Sacks lore)
     */
    private OrderInfo(
            @NotNull String name,
            @NotNull String productId,
            @NotNull TransactionType.Side side,
            @NotNull Double pricePerItem,
            @NotNull Integer volume,
            boolean priceExact) {
        super(productId, TransactionType.of(side, TransactionType.Method.ORDER), pricePerItem);
        this.name = name;
        this.volume = volume;
        this.tolerance = priceExact ? 0.0 : PriceQuantity.computeTolerance(pricePerItem, volume);
        this.priceExact = priceExact;
    }

    /**
     * Resolves {@code name} to a product ID via {@link ProductInfo#fromDisplayName} and
     * constructs an instance. Returns empty when the name is unknown.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer, boolean)
     */
    public static Optional<OrderInfo> of(@NotNull String productDisplayName, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume, boolean priceExact) {
        return ProductInfo.fromDisplayName(productDisplayName).map(info -> new OrderInfo(productDisplayName, info.getProductId(), side, pricePerItem, volume, priceExact));
    }

    /**
     * Constructs directly from a known product ID, bypassing name resolution.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer, boolean)
     */
    public static OrderInfo of(@NotNull String name, @NotNull String productId, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume, boolean priceExact) {
        return new OrderInfo(name, productId, side, pricePerItem, volume, priceExact);
    }

    public static Optional<OrderInfo> of(@NotNull Order order) {
        return ProductInfo.fromProductId(order.productId())
                .map(info -> new OrderInfo(info.getName(), order.productId(), order.side(), order.pricePerItem(), order.originalAmount(), order.priceExact()));
    }

    @Override
    public double pricePerItem() {
        return getPricePerItem();
    }

    @Override
    public int volume() {
        return volume;
    }

    @Override
    public double tolerance() {
        return tolerance;
    }

    /**
     * {@code true} when {@code priceExact} was set at construction, OR — the same fallback
     * {@link Order#priceIsExact()} uses — when {@link #tolerance()} independently computes to
     * zero because this order's total never crossed {@link #FOLDING_THRESHOLD}. A total-derived
     * price ({@code priceExact == false}) below the fold is therefore still reported exact:
     * there was no rounding for it to have absorbed either way.
     */
    @Override
    public boolean priceIsExact() {
        return priceExact || PriceQuantity.super.priceIsExact();
    }
}