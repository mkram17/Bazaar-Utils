package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;

public interface Restrictor {
    boolean shouldRestrict(OrderInfo item);
}
