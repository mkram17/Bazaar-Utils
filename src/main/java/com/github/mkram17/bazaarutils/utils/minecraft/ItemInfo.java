package com.github.mkram17.bazaarutils.utils.minecraft;

import net.minecraft.world.item.ItemStack;

/**
 * Encapsulates lightweight UI metadata for an item shown in the Bazaar orders screen,
 * such as the originating slot index and the rendered {@link ItemStack}.
 */
public record ItemInfo(Integer slotIndex, ItemStack itemStack) {
    public static ItemInfo empty(int slotIndex) {
        return new ItemInfo(slotIndex, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        return this.itemStack.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ItemInfo(Integer index, ItemStack stack))) {
            return false;
        }

        return this.slotIndex.equals(index) && ItemStack.matches(this.itemStack, stack);
    }
}
