package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Container for market price metadata of a single product. For actual user orders, prefer
 * {@link OrderInfo} or {@link Order}.
 */
@ToString
public class PriceInfo {
    @Setter @Getter
    protected OrderType orderType;

    @Setter @Getter
    protected PricingPosition pricingPosition;

    @Setter @Getter
    protected Double pricePerItem;


    public PriceInfo(Double pricePerItem, OrderType orderType) {
        this.orderType = orderType;

        if (pricePerItem != null) {
            //TODO figure out best rounding. Eg to the tenth, hundredth or thousandth
            this.pricePerItem = (double) Math.round(pricePerItem * 10) / 10;
        }
        if (orderType == null) {
            //if the orderType is null, it's value doesn't matter, but the rest of the code needs a value to run as expected, so we give a default value
            //TODO revisit whether this still needs to have default value
            this.orderType = OrderType.SELL;
        }
    }
}
