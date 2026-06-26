package com.github.mkram17.bazaarutils.mixin;

import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyBlockBazaarReply.class)
public interface AccessorSkyBlockBazaarReply {
    @Accessor("lastUpdated")
    long getLastUpdated();
}
