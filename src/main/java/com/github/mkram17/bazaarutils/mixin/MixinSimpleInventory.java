package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//used for ReplaceItemEvent
@Mixin(SimpleInventory.class)
public abstract class MixinSimpleInventory {
    @Final
    @Shadow
    public DefaultedList<ItemStack> heldStacks;


    @Inject(method = "getStack(I)Lnet/minecraft/item/ItemStack;",at = @At("HEAD"), cancellable = true)
    private void onGetStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < 0 || slot >= this.heldStacks.size()) return;

        ReplaceItemEvent event = new ReplaceItemEvent(this.heldStacks.get(slot),(SimpleInventory) (Object) this,slot);
        BazaarUtils.eventBus.post(event);
//        Util.notifyAll("Replace Item Event posted!");
        if (event.getReplacement() != event.getOriginal()) {
            cir.setReturnValue(event.getReplacement());
        }
    }
}