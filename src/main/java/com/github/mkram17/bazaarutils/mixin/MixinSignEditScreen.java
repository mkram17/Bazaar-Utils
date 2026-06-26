package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.SignOpenEvent;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SignOpenEvent
@Mixin(SignEditScreen.class)
public class MixinSignEditScreen {

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        SignOpenEvent event = new SignOpenEvent((SignEditScreen) (Object) this);
        BazaarUtils.EVENT_BUS.post(event);
    }
}
