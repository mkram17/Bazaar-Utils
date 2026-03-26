package com.github.mkram17.bazaarutils.events.screen;

import com.github.mkram17.bazaarutils.events.screen.handlers.ChestLoadedEventHandler;
import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.List;

/**
 * Event fired when a chest/container GUI is fully loaded with all items.
 * <p><strong>Note: You cannot use the default Fabric event for this on Hypixel, as not all item slots are loaded at screen initialization.</strong></p>
 *
 * <p>
 * This event is triggered after a chest or container screen opens and all items have finished loading.
 * The mod waits for all item slots to be populated (checking that items are not in a "Loading..." state)
 * before firing this event. This ensures that listeners can safely access all container contents.
 * </p>
 *
 * <p>The event includes:</p>
 * <ul>
 *   <li>The container's inventory</li>
 *   <li>A list of all non-empty item stacks</li>
 *   <li>The container's display name</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onChestLoaded(ChestLoadedEvent event) {
 *    List<ItemStack> items = event.getItemStacks();
 *    processBazaarItems(items);
 * }
 * }
 * </pre>
 *
 * <p><strong>Implementation Note:</strong></p>
 * The event uses a polling mechanism that checks every tick (up to 50 attempts / ~2.5 seconds)
 * to determine when the GUI is fully loaded. See {@link ChestLoadedEventHandler}.
 *
 * @see Container
 * @see ItemStack
 */
public class ChestLoadedEvent extends SkyBlockEvent {

    /**
     * The container screen that is being displayed.
     */
    @Getter
    private final ContainerScreen genericContainerScreen;

    /**
     * The inventory of the container (this is the top inventory, NOT the player's inventory).
     */
    @Getter
    private final Container lowerChestInventory;

    /**
     * List of all non-empty item stacks in the container.
     */
    @Getter
    private final List<ItemStack> itemStacks;

    /**
     * The display name of the container.
     */
    @Getter
    private final String containerName;

    public ChestLoadedEvent(ContainerScreen screen, Container inventory, List<ItemStack> itemStacks, String containerName) {
        this.genericContainerScreen = screen;
        this.lowerChestInventory = inventory;
        this.itemStacks = itemStacks;
        this.containerName = containerName;
    }
}