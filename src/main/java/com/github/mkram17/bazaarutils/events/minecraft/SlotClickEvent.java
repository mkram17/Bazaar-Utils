package com.github.mkram17.bazaarutils.events.minecraft;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a slot is clicked in a handled screen (GUI with inventory).
 * <p>
 * This event is triggered when the player clicks on a slot in any GUI that contains an inventory,
 * such as chests, the bazaar interface, or the player's inventory. The event can be cancelled to
 * prevent the click action from being processed.
 * </p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onSlotClick(SlotClickEvent event) {
 *     if (shouldPreventClick(event.slot)) {
 *         event.setCancelled(true);
 *         return;
 *     }
 *     // Process the click normally
 * }
 * }
 * </pre>
 * 
 * @see AbstractContainerScreen
 * @see Slot
 * @see ClickType
 */
public class SlotClickEvent implements ICancellable {
    /**
     * The screen where the slot was clicked.
     */
    @NotNull
    public final AbstractContainerScreen<?> handledScreen;
    
    /**
     * The slot that was clicked.
     */
    @NotNull
    public final Slot slot;
    
    /**
     * The index of the slot that was clicked.
     */
    @Getter
    public final int slotId;
    
    /**
     * The mouse button that was clicked.
     */
    public int clickedButton;
    
    /**
     * The type of click action performed.
     */
    public ClickType clickType;
    
    /**
     * If true, the pickblock action will be used instead of the normal click action.
     */
    public boolean usePickblockInstead = false;
    
    /**
     * Whether this event has been cancelled.
     */
    @Setter
    @Getter
    public boolean cancelled = false;

    /**
     * Creates a new SlotClickEvent.
     *
     * @param handledScreen the screen where the click occurred
     * @param slot the slot that was clicked
     * @param slotId the numeric ID of the slot
     * @param clickedButton the mouse button clicked
     * @param actionType the type of click action
     */
    public SlotClickEvent(AbstractContainerScreen<?> handledScreen, Slot slot, int slotId, int clickedButton, ClickType actionType) {
        this.handledScreen = handledScreen;
        this.slot = slot;
        this.slotId = slotId;
        this.clickedButton = clickedButton;
        this.clickType = actionType;
    }

}