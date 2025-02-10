package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.SignOpenEvent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//TODO maybe try to do this with AbstractSignEditScreen init() and see if it works to be more efficient
//used for SignOpenEvent
@Mixin(Screen.class)
public class MixinSignOpen {
    @Inject(method = "init", at = @At("HEAD"))
    private void onScreenInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;

        if (screen instanceof SignEditScreen) {
            // Post the SignOpenEvent
            SignOpenEvent event = new SignOpenEvent((SignEditScreen) screen);
            BazaarUtils.eventBus.post(event);
//            Util.notifyAll("Sign Open Event posted!");

        }
    }
}