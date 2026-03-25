package com.github.mkram17.bazaarutils.config.util.api;

import net.minecraft.item.ItemStack;

@FunctionalInterface
public interface SlotProvider {
    ItemStack getStack(int slotIndex);
}