package com.github.mkram17.bazaarutils.Events;

import meteordevelopment.orbit.ICancellable;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

public class ReplaceItemEvent implements ICancellable {
    private final ItemStack original;
    private final SimpleInventory inventory;
    private final int slotId;
    private ItemStack replacement;

    public ReplaceItemEvent(ItemStack original, SimpleInventory inventory, int slotId) {
        this.original = original;
        this.inventory = inventory;
        this.slotId = slotId;
        this.replacement = original;
    }

    // Getters and setters
    public ItemStack getOriginal() { return original; }
    public SimpleInventory getInventory() { return inventory; }
    public int getSlotId() { return slotId; }
    public ItemStack getReplacement() { return replacement; }
    public void setReplacement(ItemStack replacement) { this.replacement = replacement; }

    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}