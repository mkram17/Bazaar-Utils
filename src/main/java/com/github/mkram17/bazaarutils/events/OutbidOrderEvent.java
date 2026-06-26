package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import lombok.Getter;
import meteordevelopment.orbit.ICancellable;

public class OutbidOrderEvent implements ICancellable {
    @Getter
    private final BazaarOrder order;
    @Getter
    private final boolean isOutbid;
    public OutbidOrderEvent(BazaarOrder order, boolean isOutbid) {
        this.order = order;
        this.isOutbid = isOutbid;
    }
    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
