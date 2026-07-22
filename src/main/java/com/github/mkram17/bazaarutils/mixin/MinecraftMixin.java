package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
//? if >=26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//for ScreenChangeEvent; 26.2 moved the screen field and setScreen from Minecraft to Gui
//? if >=26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MinecraftMixin {
    @Shadow
    @Nullable
    //? if >=26.2 {
    private Screen screen;
    //?} else {
    /*public Screen screen;
    *///?}

    @Inject(method = "setScreen", at = @At("HEAD"))
    public void setScreen(Screen newScreen, CallbackInfo ci) {
        BazaarUtils.EVENT_BUS.post(new ScreenChangeEvent(screen, newScreen));
    }
}
