package com.github.mkram17.bazaarutils.events.minecraft;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Module
public final class ContainerLoadedHandler extends BUListener {

    @Subscription(priority = Integer.MIN_VALUE)
    private void onScreenInitialized(ScreenInitializedEvent event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (!(container.getMenu() instanceof ChestMenu chest)) return;

        @SuppressWarnings("unchecked") // if (!(container.getMenu() instanceof ChestMenu chest)) return; asserts this is a chestmenu
        AbstractContainerScreen<ChestMenu> typedScreen = (AbstractContainerScreen<ChestMenu>) container;

        // Use an AtomicInteger for mutable integer in lambda
        final AtomicInteger attempts = new AtomicInteger(0);
        final int MAX_ATTEMPTS = 50; // ~2.5 seconds timeout (50 * 1 tick)

        final Minecraft client = Minecraft.getInstance();

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
                    ScreenType type = ScreenManager.matchType(container).orElse(null);

                    List<Slot> containerSlots = new ArrayList<>();
                    List<Slot> playerSlots = new ArrayList<>();

                    for (Slot slot : chest.slots) {
                        if (slot.container instanceof Inventory) {
                            playerSlots.add(slot);
                        } else {
                            containerSlots.add(slot);
                        }
                    }

                    new ContainerLoadedEvent(typedScreen, type, inventory, container.getTitle(), List.copyOf(containerSlots), List.copyOf(playerSlots)).post(BazaarUtils.EVENT_BUS);
                } else if (attempts.getAndIncrement() < MAX_ATTEMPTS) {
                    // GUI not loaded, schedule the check for the next tick
                    Util.tickExecuteLater(1, this);
                }
            }
        };
        // Schedule the first check
        Util.tickExecuteLater(1, checkGuiLoaded);
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
