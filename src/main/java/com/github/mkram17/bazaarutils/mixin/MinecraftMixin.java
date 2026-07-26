package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.ScreenChangeEvent;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//for ScreenChangeEvent
@Mixin(value = Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void setScreenPre(Screen newScreen, CallbackInfo ci, @Share("oldScreen") LocalRef<Screen> oldScreenRef) {
        oldScreenRef.set(this.screen);
        new ScreenChangeEvent.Pre(this.screen, newScreen).post(BazaarUtils.EVENT_BUS);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void setScreenPost(Screen newScreen, CallbackInfo ci, @Share("oldScreen") LocalRef<Screen> oldScreenRef) {
        new ScreenChangeEvent.Post(oldScreenRef.get(), newScreen).post(BazaarUtils.EVENT_BUS);
    }
}
