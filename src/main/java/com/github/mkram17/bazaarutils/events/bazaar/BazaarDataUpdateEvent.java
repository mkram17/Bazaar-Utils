package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.data.wrappers.CustomBazaarReply;
import lombok.Getter;
import meteordevelopment.orbit.ICancellable;

/**
 * Event fired when bazaar data is updated from the Hypixel API.
 * <p>
 * This event is triggered whenever fresh bazaar market data is retrieved from the Hypixel API.
 * It provides access to the converted custom bazaar reply containing all current market prices,
 * volumes, and other bazaar statistics.
 * </p>
 */
public class BazaarDataUpdateEvent implements ICancellable {

    /**
     * The converted bazaar data reply containing current market information.
     */
    @Getter
    private final CustomBazaarReply bazaarReply;

    public BazaarDataUpdateEvent(CustomBazaarReply bazaarReply) {
        this.bazaarReply = bazaarReply;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}