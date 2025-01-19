package com.github.sirmegabite.bazaarutils.mixin;

import com.github.sirmegabite.bazaarutils.EventHandlers.ChestLoadedEvent;
import com.github.sirmegabite.bazaarutils.Utils.Util;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ContainerChest.class)
public abstract class MixinGuiChest {
    @Shadow
    private IInventory lowerChestInventory;

    @Unique
    List<ItemStack> itemStacks = new ArrayList<>();

    @Inject(method = "getLowerChestInventory", at = @At("RETURN"))
    private void onLowerChestGrab(CallbackInfoReturnable<IInventory> cir) {
        updateItemStacks();
        if(isGuiLoaded()) {
            Util.notifyAll("GUI loaded mixin");
            MinecraftForge.EVENT_BUS.post(new ChestLoadedEvent(lowerChestInventory));
        }
    }

    @Unique
    public boolean isGuiLoaded() {
            int size = lowerChestInventory.getSizeInventory();

            if (size == 0) {
                return false;
            }

            ItemStack bottomRightItem = lowerChestInventory.getStackInSlot(size - 1);

            if (bottomRightItem != null && areItemsLoaded()) {
                Util.notifyAll("Item detected in the bottom-right corner: " + bottomRightItem.getDisplayName(), Util.notificationTypes.GUI);
                return true;
            }
            return false;
    }

    @Unique
    public boolean areItemsLoaded(){
        for (ItemStack item : itemStacks) {
            NBTTagCompound tagCompound = item.getTagCompound();

            if (tagCompound == null) continue;
            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            if(displayName.contains("Loading")) {
                Util.notifyAll("Loading item...", Util.notificationTypes.GUI);
                return false;
            }
        }
        return true;
    }

    @Unique
    public void updateItemStacks() {
        itemStacks.clear();
        for (int i = 0; i < lowerChestInventory.getSizeInventory(); i++) {
            ItemStack stack = lowerChestInventory.getStackInSlot(i);
            if (stack != null) {
    //                Util.notifyAll("Slot " + i + ": " + stack, this.getClass());
                itemStacks.add(stack);
            }
        }
    }
}
