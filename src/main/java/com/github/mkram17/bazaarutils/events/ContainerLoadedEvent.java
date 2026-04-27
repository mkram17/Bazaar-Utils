package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import lombok.Getter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
 * public void onChestLoaded(ContainerLoadedEvent event) {
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
public class ContainerLoadedEvent {
    /**
     * The container that is being displayed.
     */
    @Getter
    private AbstractContainerScreen<ChestMenu> screen;

    private @Nullable ScreenType type;

    /**
     * The resolved {@link ScreenType} for this container, or empty if unrecognised.
     */
    public Optional<ScreenType> getType() {
        return Optional.ofNullable(type);
    }

    /**
     * The inventory of the container opened.
     */
    @Getter
    private Container container;

    /**
     * The display name of the container.
     */
    @Getter
    private String title;

    /**
     * The title of the container as a {@link Component}, preserving formatting.
     */
    @Getter
    private Component titleComponent;

    /**
     * All slots in the container, exluding the player's inventory slots.
     */
    @Getter
    private List<Slot> containerSlots = new ArrayList<>();

    /**
     * All slots belonging to the player's inventory within this container screen.
     */
    @Getter
    private List<Slot> playerSlots = new ArrayList<>();

    public ScreenContext asContext() {
        return new ScreenContext(screen, type);
    }

    /**
     * Registers the screen event listener that triggers this event when chests are loaded.
     * This method is automatically called during mod initialization.
     */
    @RunOnInit
    public static void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            if (!(container.getMenu() instanceof ChestMenu chest)) return;

            @SuppressWarnings("unchecked") // if (!(container.getMenu() instanceof ChestMenu chest)) return; asserts this is a chestmenu
            AbstractContainerScreen<ChestMenu> typedScreen = (AbstractContainerScreen<ChestMenu>) container;

            // Use an AtomicInteger for mutable integer in lambda
            final AtomicInteger attempts = new AtomicInteger(0);
            final int MAX_ATTEMPTS = 50; // ~2.5 seconds timeout (50 * 1 tick)

            // Define the check as a Runnable
            Runnable checkGuiLoaded = new Runnable() {
                @Override
                public void run() {
                    // Ensure we are still on the same screen
                    if (client.screen != container) return;

                    Container inventory = chest.getContainer();

                    // Check if inventory is populated and not in a loading state
                    if (!inventory.isEmpty()
                            && !inventory.getItem(inventory.getContainerSize() - 1).isEmpty()
                            && !isItemLoading(inventory)) {
                        ScreenType type = ScreenManager.matchType(screen).orElse(null);

                        List<Slot> containerSlots = new ArrayList<>();
                        List<Slot> playerSlots = new ArrayList<>();

                        // GUI is loaded, post the event
                        ContainerLoadedEvent event = new ContainerLoadedEvent();

                        event.screen = typedScreen;
                        event.type = type;
                        event.container = inventory;

                        event.titleComponent = container.getTitle();
                        event.title = Util.removeFormatting(event.titleComponent.getString());

                        for (Slot slot : chest.slots) {
                            if (slot.container instanceof Inventory) {
                                playerSlots.add(slot);
                            } else {
                                containerSlots.add(slot);
                            }
                        }

                        event.containerSlots = List.copyOf(containerSlots);
                        event.playerSlots = List.copyOf(playerSlots);

                        BazaarUtils.EVENT_BUS.post(event);
                    } else if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                        // GUI not loaded, schedule the check for the next tick
                        Util.tickExecuteLater(1, this);
                    }
                }
            };
            // Schedule the first check
            Util.tickExecuteLater(1, checkGuiLoaded);
        });
    }

    private static boolean isItemLoading(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) continue;

            Component customName = item.get(DataComponents.CUSTOM_NAME);
            if (customName == null) continue;

            String name = Util.removeFormatting(customName.getString());

            if (name.contains("Loading")) {
                return true;
            }

            // Only bottleneck on lore data of items known to have partialized lore
            if (name.contains("Sell")) {
                ItemLore lore = item.get(DataComponents.LORE);

                if (lore != null && !lore.lines().isEmpty()) {
                    for (Component line : lore.lines()) {
                        if (Util.removeFormatting(line.getString()).contains("Loading")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}