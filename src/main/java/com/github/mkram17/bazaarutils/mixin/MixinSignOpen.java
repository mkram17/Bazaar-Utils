package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import meteordevelopment.orbit.EventBus;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.Screen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinSignOpen {
    @Inject(method = "init", at = @At("HEAD"))
    private void onScreenInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;

        // Check if the screen is a SignEditScreen
        if (screen instanceof SignEditScreen) {
            // Post the SignOpenEvent
            SignOpenEvent event = new SignOpenEvent((SignEditScreen) screen);
            BazaarUtils.eventBus.post(event);

            // Cancel the screen opening if the event is cancelled
            if (event.isCancelled()) {
                screen.close();
            }
        }
    }
}