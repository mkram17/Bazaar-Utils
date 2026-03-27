package com.github.mkram17.bazaarutils.events.screen;

import com.github.mkram17.bazaarutils.events.screen.handlers.ChestLoadedEventHandler;
import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
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
 * <p>The event exposes:</p>
 * <ul>
 *   <li>The container screen</li>
 *   <li>The container title as both a raw {@link Component} and a stripped {@link String}</li>
 *   <li>All slots and items, split between container-only and full menu views</li>
 *   <li>The container row count, if available</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * {@code
 * @EventHandler
 * public void onChestLoaded(ChestLoadedEvent event) {
 *     for (Slot slot : event.getContainerSlots()) {
 *         process(slot.getItem());
 *     }
 * }
 * }
 * </pre>
 *
 * <p><strong>Implementation Note:</strong></p>
 * The event uses a polling mechanism that checks every tick (up to 50 attempts / ~2.5 seconds)
 * to determine when the GUI is fully loaded. See {@link ChestLoadedEventHandler}.
 *
 * @see AbstractContainerScreen
 * @see Slot
 * @see ItemStack
 */
@Getter
public class ChestLoadedEvent extends SkyBlockEvent {
    /**
     * The container screen that is being displayed.
     */
    private final AbstractContainerScreen<?> screen;

    /**
     * The title of the container as a {@link Component}, preserving formatting.
     */
    private final Component titleComponent;

    /**
     * The display name of the container, with formatting stripped.
     */
    private final String title;

    /**
     * The number of rows in the container, or {@code null} if not a chest-type menu.
     */
    @Nullable
    private final Integer rowCount;

    /**
     * All slots in the menu, including the player's inventory slots.
     */
    private final List<Slot> slots;

    /**
     * Slots belonging to the container only — the player's inventory slots are excluded.
     */
    private final List<Slot> containerSlots;

    /**
     * Item stacks from the container slots. Mirrors {@link #getContainerSlots()} as items.
     */
    private final List<ItemStack> containerItems;

    public ChestLoadedEvent(
            AbstractContainerScreen<?> screen,
            Component titleComponent,
            String title,
            @Nullable Integer rowCount,
            List<Slot> slots,
            List<Slot> containerSlots,
            List<ItemStack> containerItems
    ) {
        this.screen = screen;
        this.titleComponent = titleComponent;
        this.title = title;
        this.rowCount = rowCount;
        this.slots = slots;
        this.containerSlots = containerSlots;
        this.containerItems = containerItems;
    }
}