package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.PriceType;

public class UserProductOrder extends ProductOrder {
    private Order userOrder;

    public UserProductOrder(Order userOrder, PriceType priceType, double pricePerUnit, long amount, long orders) {
        super(priceType, pricePerUnit, amount, orders);
        this.userOrder = userOrder;
    }
    public UserProductOrder(Order userOrder, ProductOrder productOrder) {
        super(productOrder.getPriceType(), productOrder.getPricePerUnit(), productOrder.getVolume(), productOrder.getNumOrders());
        this.userOrder = userOrder;
    }
}
