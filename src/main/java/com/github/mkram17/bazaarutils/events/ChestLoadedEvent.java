package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import lombok.Getter;
import meteordevelopment.orbit.ICancellable;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChestLoadedEvent {
    @Getter
    private Container lowerChestInventory;
    @Getter
    private List<ItemStack> itemStacks = new ArrayList<>();
    @Getter
    private String containerName;

    @RunOnInit
    public static void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ContainerScreen genericContainerScreen) {
                // Use an AtomicInteger for mutable integer in lambda
                final AtomicInteger attempts = new AtomicInteger(0);
                final int MAX_ATTEMPTS = 50; // ~2.5 seconds timeout (50 * 1 tick)

                // Define the check as a Runnable
                Runnable checkGuiLoaded = new Runnable() {
                    @Override
                    public void run() {
                        // Ensure we are still on the same screen
                        if (client.screen != genericContainerScreen) {
                            return;
                        }

                        AbstractContainerMenu handler = genericContainerScreen.getMenu();
                        if (handler instanceof ChestMenu containerHandler) {
                            Container inv = containerHandler.getContainer();
                            // Check if inventory is populated and not in a loading state
                            if (!inv.isEmpty() && !inv.getItem(inv.getContainerSize() - 1).isEmpty() && !isItemLoading(inv)) {
                                // GUI is loaded, post the event
                                ChestLoadedEvent event = new ChestLoadedEvent();
                                event.lowerChestInventory = inv;
                                event.containerName = ScreenInfo.getCurrentScreenInfo().getContainerName();
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
            if (customName != null) {
                String displayName = Util.removeFormatting(customName.getString());
                if (displayName.contains("Loading")) {
                    PlayerActionUtil.notifyAll("Loading item...", Util.notificationTypes.GUI);
                    return true;
                }
            }
        }
        return false;
    }
}
