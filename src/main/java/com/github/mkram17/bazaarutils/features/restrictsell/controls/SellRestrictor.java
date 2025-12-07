package com.github.mkram17.bazaarutils.features.restrictsell.controls;

import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;

public interface SellRestrictor {
    boolean shouldRestrict(PriceInfoContainer container);
}
