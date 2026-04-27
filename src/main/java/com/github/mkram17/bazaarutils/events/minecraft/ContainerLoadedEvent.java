package com.github.mkram17.bazaarutils.events.minecraft;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import lombok.Getter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Event fired when a chest/container GUI is fully loaded with all items.
 * <p><strong>Note: You cannot use the default Fabric event for this on Hypixel, as not all item slots are loaded with all their data at screen initialization.</strong></p>
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
 * public void onContainerLoaded(ContainerLoadedEvent event) {
 *    List<ItemStack> items = event.getItemStacks();
 *    processBazaarItems(items);
 * }
 * }
 * </pre>
 *
 * <p><strong>Implementation Note:</strong></p>
 * The event uses a polling mechanism that checks every 40ms (up to 50 attempts / 2 seconds)
 * to determine when the GUI is fully loaded.
 *
 * @see Container
 * @see ItemStack
 */
@Getter
public final class ContainerLoadedEvent extends SkyBlockEvent {
    /**
     * The container that is being displayed.
     */
    private final AbstractContainerScreen<ChestMenu> screen;

    private final @Nullable ScreenType type;

    /**
     * The resolved {@link ScreenType} for this container, or empty if unrecognised.
     */
    public Optional<ScreenType> getType() {
        return Optional.ofNullable(type);
    }

    /**
     * The inventory of the container opened.
     */
    private final Container container;

    /**
     * The display name of the container.
     */
    private final String title;

    /**
     * The title of the container as a {@link Component}, preserving formatting.
     */
    private final Component titleComponent;

    /**
     * All slots in the container, exluding the player's inventory slots.
     */
    private List<Slot> containerSlots;

    /**
     * All slots belonging to the player's inventory within this container screen.
     */
    private List<Slot> playerSlots;

    public ScreenContext asContext() {
        return new ScreenContext(screen, type);
    }

    public ContainerLoadedEvent(
            AbstractContainerScreen<ChestMenu> screen,
            @Nullable ScreenType type,
            Container container,
            Component titleComponent,
            List<Slot> containerSlots,
            List<Slot> playerSlots) {
        this.screen = screen;
        this.type = type;
        this.container = container;
        this.titleComponent = titleComponent;
        this.title = Util.removeFormatting(titleComponent.getString());
        this.containerSlots = containerSlots;
        this.playerSlots = playerSlots;
    }
}