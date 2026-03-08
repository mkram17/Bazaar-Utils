package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import meteordevelopment.orbit.EventHandler;

public class MarketPrices extends BUListener {

    private final PriceInfo buyPriceInfo = new PriceInfo(null, OrderType.BUY);
    private final PriceInfo sellPriceInfo = new PriceInfo(null, OrderType.SELL);
    private final String productID;

    public MarketPrices(String productID) {
        this.productID = productID;
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
}
