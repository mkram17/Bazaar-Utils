package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import lombok.Getter;

public class ProductOrder {
    @Getter
    private final PriceType priceType;
    @Getter
    private final double pricePerUnit;
    @Getter
    private final long amount;
    @Getter
    private final long orders;

    public ProductOrder(PriceType priceType, double pricePerUnit, long amount, long orders) {
        this.priceType = priceType;
        this.pricePerUnit = pricePerUnit;
        this.amount = amount;
        this.orders = orders;
    }

    public boolean hasOrders() {
        return orders >0 && pricePerUnit >0;
    }

    public boolean equalsOrder(Order order) {
        return order.getOrderType().asPriceType() == priceType && order.getPricePerItem() == pricePerUnit && order.getVolume() == amount;
    }
}
