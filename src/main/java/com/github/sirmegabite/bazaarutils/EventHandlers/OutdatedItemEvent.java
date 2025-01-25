package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.Utils.ItemData;
import net.minecraftforge.fml.common.eventhandler.Event;

public class OutdatedItemEvent extends Event {
    private final ItemData item;
    public OutdatedItemEvent(ItemData item) {
        this.item = item;
    }
    public ItemData getItem(){
        return item;
    }
}
