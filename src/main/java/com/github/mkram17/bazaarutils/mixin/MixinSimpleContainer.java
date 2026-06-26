package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleContainer.class)
public abstract class MixinSimpleContainer {
    @Final
    @Shadow
    public NonNullList<ItemStack> items;

    // Prevents recursive StackOverflow when other mods (Skyblock-API, SkyOcean)
    // hook ItemStack construction and read inventory slots to resolve SkyBlock IDs.
    @Unique
    private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getItem(I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void onGetStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < 0 || slot >= this.items.size()) return;
        if (REPLACING.get()) return;

        REPLACING.set(true);
        try {
            ReplaceItemEvent event = new ReplaceItemEvent(this.items.get(slot), (SimpleContainer) (Object) this, slot);
            BazaarUtils.EVENT_BUS.post(event);

            if (event.getReplacement() != event.getOriginal()) {
                cir.setReturnValue(event.getReplacement());
            }
        } finally {
            REPLACING.set(false);
        }
    }
}
