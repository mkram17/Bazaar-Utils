package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SignOpenEvent;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// used for SignOpenEvent.
// 26.1 had SignEditScreen override init(); 26.2 dropped that override, so init() is only
// declared on AbstractSignEditScreen. Mixin resolves @Inject targets against the methods the
// target class itself declares, not inherited ones, so this has to sit on the parent.
@Mixin(AbstractSignEditScreen.class)
public class MixinSignEditScreen {

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        // The other subclass is HangingSignEditScreen, which never fired this event before;
        // keep it scoped to SignEditScreen so behaviour is unchanged.
        if ((Object) this instanceof SignEditScreen sign) {
            new SignOpenEvent(sign).post(BazaarUtils.EVENT_BUS);
        }
    }
}
