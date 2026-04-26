package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Event fired when an item in an inventory is about to be replaced.
 * <p>
 * This event is triggered before an item in a simple inventory is replaced with another item.
 * Listeners can modify the replacement item or cancel the replacement entirely. This is
 * used for modifying item displays in GUI menus.
 * </p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onItemReplace(ReplaceItemEvent event) {
 *     ItemStack original = event.getOriginal();
 *     ItemStack modified = addCustomLore(original);
 *     event.setReplacement(modified);
 * }
 * }
 * </pre>
 * 
 * @see ItemStack
 * @see SimpleContainer
 */
public class ReplaceItemEvent implements ICancellable {
    /**
     * The original item stack before replacement.
     */
    @Getter
    private final ItemStack original;
    
    /**
     * The inventory containing the item being replaced.
     */
    @Getter
    private final SimpleContainer inventory;
    
    /**
     * The slot ID where the replacement is occurring.
     */
    @Getter
    private final int slotId;
    
    /**
     * The item stack that will replace the original.
     * Can be modified by event handlers.
     */
    @Setter @Getter
    private ItemStack replacement;

    /**
     * Creates a new ReplaceItemEvent.
     *
     * @param original the original item stack
     * @param inventory the inventory containing the item
     * @param slotId the slot where the replacement occurs
     */
    public ReplaceItemEvent(ItemStack original, SimpleContainer inventory, int slotId) {
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