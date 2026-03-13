package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import lombok.Getter;

public class ProductOrder {
    @Getter
    private final PriceType priceType;
    @Getter
    private final double pricePerUnit;
    @Getter
    private final long volume;
    @Getter
    private final long numOrders;

    public ProductOrder(PriceType priceType, double pricePerUnit, long volume, long numOrders) {
        this.priceType = priceType;
        this.pricePerUnit = pricePerUnit;
        this.volume = volume;
        this.numOrders = numOrders;
    }

    public boolean hasOrders() {
        return numOrders >0 && pricePerUnit >0;
    }

    public boolean equalsOrder(Order order) {
        return order.getOrderType().asPriceType() == priceType && order.getPricePerItem() == pricePerUnit && order.getVolume() == volume;
    }
}
