package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import com.github.mkram17.bazaarutils.Utils.Util;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SignOpenEvent -- maybe try to do this with AbstractSignEditScreen init() and see if it works to be more efficient
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
            Util.notifyAll("Sign Open Event posted!");

            // Cancel the screen opening if the event is cancelled
            if (event.isCancelled()) {
                screen.close();
            }
        }
    }
}