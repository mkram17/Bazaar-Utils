package com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers;

import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;

public record ProductOrder(PriceType priceType, double pricePerUnit, long volume, long numOrders) {

    public boolean hasOrders() {
        return numOrders > 0 && pricePerUnit > 0;
    }

    public boolean equalsOrder(Order order) {
        return order.getTransactionType().getPriceType() == priceType && order.getPricePerItem() == pricePerUnit && order.getVolume() == volume;
    }
}
