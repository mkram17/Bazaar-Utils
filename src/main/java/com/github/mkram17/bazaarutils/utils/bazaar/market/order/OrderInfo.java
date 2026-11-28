package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of one Bazaar order interaction: product display name, product ID,
 * side, unit {@link PriceInfo} (exact or total-derived), and volume.
 */
public record OrderInfo(
        @NotNull String name,
        @NotNull String productId,
        @NotNull TransactionType.Side side,
        @NotNull PriceInfo price,
        int volume
) implements ProductInfo {
    public OrderInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
    }

    /**
     * Resolves {@code productDisplayName} to a product ID via {@link ProductInfo#fromDisplayName}
     * and constructs an instance using the canonical display name. Returns empty when unknown.
     */
    public static Optional<OrderInfo> of(
            @NotNull String productDisplayName,
            @NotNull TransactionType.Side side,
            double pricePerItem,
            int volume,
            boolean priceExact) {
        return ProductInfo.fromDisplayName(productDisplayName)
                .map(info -> new OrderInfo(info.getName(), info.getProductId(), side, PriceInfo.of(pricePerItem, volume, priceExact), volume));
    }

    /** Constructs directly from a known product ID, bypassing name resolution. */
    public static OrderInfo of(
            @NotNull String name,
            @NotNull String productId,
            @NotNull TransactionType.Side side,
            double pricePerItem,
            int volume,
            boolean priceExact) {
        return new OrderInfo(name, productId, side, PriceInfo.of(pricePerItem, volume, priceExact), volume);
    }

    public static Optional<OrderInfo> of(@NotNull Order order) {
        return ProductInfo.fromProductId(order.productId())
                .map(info -> new OrderInfo(info.getName(), order.productId(), order.side(), order.price(), order.originalAmount()));
    }

    @Override
    public @NotNull String getProductId() {
        return productId;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    public @NotNull TransactionType getTransaction() {
        return TransactionType.of(side, TransactionType.Method.ORDER);
    }

    public double getPricePerItem() {
        return price.pricePerItem();
    }

    public int getVolume() {
        return volume;
    }

    public boolean isPriceExact() {
        return price.isExact();
    }
}
