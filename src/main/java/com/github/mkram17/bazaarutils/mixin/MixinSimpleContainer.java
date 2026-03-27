package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.events.screen.ReplaceItemEvent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

//used for ReplaceItemEvent
@Mixin(SimpleContainer.class)
public abstract class MixinSimpleContainer {
    @Final
    @Shadow
    public NonNullList<ItemStack> items;


    @Inject(method = "getItem(I)Lnet/minecraft/world/item/ItemStack;",at = @At("HEAD"), cancellable = true)
    private void onGetStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < 0 || slot >= this.items.size()) return;

        ReplaceItemEvent event = new ReplaceItemEvent(this.items.get(slot),(SimpleContainer) (Object) this,slot);
        event.post(EVENT_BUS);

        if (event.getReplacement() != event.getOriginal()) {
            cir.setReturnValue(event.getReplacement());
        }
    }
}