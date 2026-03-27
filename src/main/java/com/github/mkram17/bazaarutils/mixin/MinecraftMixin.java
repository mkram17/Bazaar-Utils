package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.events.screen.ScreenChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//for ScreenChangeEvent
@Mixin(value = Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    public void setScreen(Screen newScreen, CallbackInfo ci) {
        new ScreenChangeEvent(screen, newScreen).post(EVENT_BUS);
    }
}
