package com.github.mkram17.bazaarutils.events;

import lombok.AllArgsConstructor;
import meteordevelopment.orbit.ICancellable;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

@AllArgsConstructor
public class BazaarDataUpdateEvent implements ICancellable {

    private SkyBlockBazaarReply bazaarReply;

    @Override
    public void setCancelled(boolean cancelled) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
