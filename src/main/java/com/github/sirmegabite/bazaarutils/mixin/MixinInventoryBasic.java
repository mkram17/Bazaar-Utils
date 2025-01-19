package com.github.sirmegabite.bazaarutils.mixin;

import com.github.sirmegabite.bazaarutils.EventHandlers.ReplaceItemEvent;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryBasic.class)
public abstract class MixinInventoryBasic {
    @Shadow
    private ItemStack[] inventoryContents;

    @Shadow
    public abstract IChatComponent getDisplayName();

    @Inject(method = "getStackInSlot", at = @At("HEAD"), cancellable = true)
    public void on(int index, CallbackInfoReturnable<ItemStack> cir) {
        ReplaceItemEvent replaceItemEvent = new ReplaceItemEvent(
                index >= 0 && index < this.inventoryContents.length ? this.inventoryContents[index] : null,
                ((InventoryBasic) (Object) this),
                index
        );
        replaceItemEvent.post();
        if (replaceItemEvent.getReplacement() != replaceItemEvent.getOriginal()) {
            cir.setReturnValue(replaceItemEvent.getReplacement());
        }
    }


}