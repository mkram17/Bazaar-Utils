package com.github.mkram17.bazaarutils.mixin;

import com.teamresourceful.resourcefulconfig.api.types.entries.Observable;
import com.teamresourceful.resourcefulconfig.client.ConfigScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Observable.class, remap = false)
public class ObservableConfigRefreshMixin {

    @Inject(method = "accept", at = @At("TAIL"))
    private void bazaarutils$onAccept(Object value, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ConfigScreen configScreen) {
            configScreen.updateOptions();
        }
    }
}