package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import meteordevelopment.orbit.ICancellable;

@AllArgsConstructor
public class UserOrdersChangeEvent implements ICancellable {

    public enum ChangeTypes {
        ADD,
        REMOVE,
        UPDATE
    }
    @Getter
    private ChangeTypes changeType;
    @Getter
    private BazaarOrder order;

    @Override
    public void setCancelled(boolean cancelled) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
