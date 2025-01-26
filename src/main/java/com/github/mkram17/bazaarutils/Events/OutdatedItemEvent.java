package com.github.mkram17.bazaarutils.Events;

import com.github.mkram17.bazaarutils.Utils.ItemData;
import meteordevelopment.orbit.ICancellable;

public class OutdatedItemEvent implements ICancellable {
    private final ItemData item;
    public OutdatedItemEvent(ItemData item) {
        this.item = item;
    }
    public ItemData getItem(){
        return item;
    }

    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
