package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import lombok.Getter;

import java.util.OptionalDouble;

public class MarketPrices {

    @Getter
    private final String productID;

    public MarketPrices(String productID) {
        this.productID = productID;
    }

    /**
     * No-op: prices are now read fresh from {@link BazaarDataManager} on every call to
     * {@link #getPriceForPosition}. Kept for API compatibility.
     */
    public void updateMarketPrices() {
    }

    public Double getPriceForPosition(PricingPosition pricingPosition, OrderType orderType) {
        return getPriceForPosition(productID, pricingPosition, orderType);
    }

    /**
     * Computes the adjusted price for a product at the given pricing position and order type
     * without creating a {@link MarketPrices} instance. Use this instead of instantiating
     * {@link MarketPrices} transiently to avoid listener leaks.
     *
     * @param productId       the Hypixel product ID to look up
     * @param pricingPosition the positioning strategy (competitive, matched, outbid)
     * @param orderType       the order side (BUY or SELL)
     * @return the market price adjusted for the given pricing position, or {@code 0.0} if no data is available
     */
    public static double getPriceForPosition(String productId, PricingPosition pricingPosition, OrderType orderType) {
        OptionalDouble priceOpt = BazaarDataManager.findItemPriceOptional(productId, orderType);
        double price = Util.truncateNum(priceOpt.orElse(0.0));

        return switch (orderType) {
            case SELL -> switch (pricingPosition) {
                case COMPETITIVE -> price - 0.1;
                case MATCHED -> price;
                case OUTBID -> price + 0.1;
            };
            case BUY -> switch (pricingPosition) {
                case COMPETITIVE -> price + 0.1;
                case MATCHED -> price;
                case OUTBID -> price - 0.1;
            };
        };
    }
}
