package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;

import java.util.OptionalDouble;

public class MarketPrices extends BUListener {

    private final PriceInfo buyPriceInfo = new PriceInfo(null, OrderType.BUY);
    private final PriceInfo sellPriceInfo = new PriceInfo(null, OrderType.SELL);
    @Getter
    private final String productID;

    public MarketPrices(String productID) {
        this.productID = productID;
        subscribe();
        updateMarketPrices(productID);
    }

    @EventHandler
    private void onDataUpdate(BazaarDataUpdateEvent event) {
        updateMarketPrices();
    }

    /**
     * Refreshes cached market price data for this product.
     */
    public void updateMarketPrices() {
        updateMarketPrices(productID);
    }

    private void updateMarketPrices(String productId) {
        var buyPriceOpt = BazaarDataManager.findItemPriceOptional(productId, OrderType.BUY);
        var sellPriceOpt = BazaarDataManager.findItemPriceOptional(productId, OrderType.SELL);

        buyPriceOpt.ifPresent(price -> buyPriceInfo.setPricePerItem(Util.truncateNum(price)));
        sellPriceOpt.ifPresent(price -> sellPriceInfo.setPricePerItem(Util.truncateNum(price)));
    }

    public Double getPriceForPosition(PricingPosition pricingPosition, OrderType orderType) {
        double marketSellPrice = sellPriceInfo.getPricePerItem();
        double marketBuyPrice = buyPriceInfo.getPricePerItem();

        return switch (orderType) {
            case SELL -> switch (pricingPosition) {
                case COMPETITIVE -> marketSellPrice - 0.1;
                case MATCHED -> marketSellPrice;
                case OUTBID -> marketSellPrice + 0.1;
            };
            case BUY -> switch (pricingPosition) {
                case COMPETITIVE -> marketBuyPrice + 0.1;
                case MATCHED -> marketBuyPrice;
                case OUTBID -> marketBuyPrice - 0.1;
            };
        };
    }

    /**
     * Computes the adjusted price for a product at the given pricing position and order type
     * without creating a {@link MarketPrices} instance. Use this instead of instantiating
     * {@link MarketPrices} transiently to avoid listener leaks.
     *
     * @param productId      the Hypixel product ID to look up
     * @param pricingPosition the positioning strategy (competitive, matched, outbid)
     * @param orderType      the order side (BUY or SELL)
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
