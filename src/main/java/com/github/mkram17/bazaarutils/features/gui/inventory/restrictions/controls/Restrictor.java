package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;

import java.util.List;

public interface Restrictor {
    boolean shouldRestrict(OrderInfo item);

    default boolean anyMatch(List<OrderInfo> items) {
        return items.stream().anyMatch(this::shouldRestrict);
    }
}
