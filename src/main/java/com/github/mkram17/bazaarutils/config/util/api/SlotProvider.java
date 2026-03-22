package com.github.mkram17.bazaarutils.config.util.api;

import net.minecraft.item.ItemStack;

@FunctionalInterface
public interface SlotProvider {
    ItemStack getStack(int slotIndex, int selectedSlotIndex);

    default ItemStack getStack(int slotIndex) {
        return getStack(slotIndex, -1);
    }
}