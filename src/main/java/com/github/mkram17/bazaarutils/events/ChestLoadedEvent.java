package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.Util;
import lombok.Getter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 * The event uses a polling mechanism that checks every 40ms (up to 50 attempts / 2 seconds)
 * to determine when the GUI is fully loaded.
 *
 * @see Container
 * @see ItemStack
 */
public class ChestLoadedEvent {
    /**
     * The container that is being displayed.
     */
    @Getter
    private ContainerScreen genericContainerScreen;

    /**
     * The inventory of the lower chest/container (this is actually the inventory on top, NOT the player's inventory (ask Mojang, not me)).
     */
    @Getter
    private Container lowerChestInventory;

    /**
     * List of all non-empty item stacks in the container.
     */
    @Getter
    private List<ItemStack> itemStacks = new ArrayList<>();

    /**
     * The display name of the container.
     */
    @Getter
    private String containerName;

    /**
     * Registers the screen event listener that triggers this event when chests are loaded.
     * This method is automatically called during mod initialization.
     */
    @RunOnInit
    public static void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ContainerScreen gcs) {
                // Use an AtomicInteger for mutable integer in lambda
                final AtomicInteger attempts = new AtomicInteger(0);
                final int MAX_ATTEMPTS = 50; // ~2.5 seconds timeout (50 * 1 tick)

                // Define the check as a Runnable
                Runnable checkGuiLoaded = new Runnable() {
                    @Override
                    public void run() {
                        // Ensure we are still on the same screen
                        if (client.screen != gcs) {
                            return;
                        }

                        AbstractContainerMenu handler = gcs.getMenu();
                        if (handler instanceof ChestMenu containerHandler) {
                            Container inv = containerHandler.getContainer();
                            // Check if inventory is populated and not in a loading state
                            if (!inv.isEmpty() && !inv.getItem(inv.getContainerSize() - 1).isEmpty() && !isItemLoading(inv)) {
                                // GUI is loaded, post the event
                                ChestLoadedEvent event = new ChestLoadedEvent();
                                event.lowerChestInventory = inv;
                                event.genericContainerScreen = gcs;
                                event.containerName = ContainerManager.getContainerName();
                                event.itemStacks = getChestItemSlots(inv);
                                BazaarUtils.EVENT_BUS.post(event);
                            } else if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                                // GUI not loaded, schedule the check for the next tick
                                Util.tickExecuteLater(1, this);
                            }
                        }
                    }
                };
                // Schedule the first check
                Util.tickExecuteLater(1, checkGuiLoaded);
            }
        });
    }

    private static List<ItemStack> getChestItemSlots(Container inventory) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
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