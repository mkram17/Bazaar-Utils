package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;

public class UserProductSummary extends ProductSummary{
    private Order userOrder;

    public UserProductSummary(Order userOrder, PriceType priceType, double pricePerUnit, long amount, long orders) {
        super(priceType, pricePerUnit, amount, orders);
        this.userOrder = userOrder;
    }
}
