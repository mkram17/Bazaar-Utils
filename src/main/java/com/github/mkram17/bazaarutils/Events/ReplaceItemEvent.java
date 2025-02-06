package com.github.mkram17.bazaarutils.Events;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

public class ReplaceItemEvent implements ICancellable {
    @Getter
    private final ItemStack original;
    @Getter
    private final SimpleInventory inventory;
    @Getter
    private final int slotId;
    @Setter @Getter
    private ItemStack replacement;

    public ReplaceItemEvent(ItemStack original, SimpleInventory inventory, int slotId) {
        this.original = original;
        this.inventory = inventory;
        this.slotId = slotId;
        this.replacement = original;
    }

    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}