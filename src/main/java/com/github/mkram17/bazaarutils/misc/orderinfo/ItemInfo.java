package com.github.mkram17.bazaarutils.misc.orderinfo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Encapsulates lightweight UI metadata for an item shown in the Bazaar orders screen,
 * such as the originating slot index and an item template that can be loaded before joining a world.
 */
public record ItemInfo(Integer slotIndex, @SerializedName("itemStack") ItemStackTemplate itemTemplate) {
    public ItemInfo(Integer slotIndex, ItemStack itemStack) {
        this(slotIndex, itemStack == null || itemStack.isEmpty() ? null : ItemStackTemplate.fromNonEmptyStack(itemStack));
    }

    /** Creates the saved stack only when it is needed by an in-world Bazaar screen. */
    public ItemStack itemStack() {
        return itemTemplate == null ? ItemStack.EMPTY : itemTemplate.create();
    }
}
