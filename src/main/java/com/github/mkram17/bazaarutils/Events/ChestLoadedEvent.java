package com.github.mkram17.bazaarutils.Events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Utils.Util;
import meteordevelopment.orbit.ICancellable;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChestLoadedEvent implements ICancellable {
    private Inventory lowerChestInventory;
    private List<ItemStack> itemStacks = new ArrayList<>();
    private String containerName;
    public static void subscribe(){
        registerScreenEvent();
    }

    public static void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GenericContainerScreen genericContainerScreen) {
                CompletableFuture.runAsync(() -> checkIfGuiLoaded(genericContainerScreen)).thenRun(() -> {
                    Util.notifyAll("Chest loaded event went off!", Util.notificationTypes.GUI);

                    ChestLoadedEvent event = new ChestLoadedEvent();
                    ScreenHandler handler = genericContainerScreen.getScreenHandler();
                    if (handler instanceof GenericContainerScreenHandler containerHandler) {
                        event.lowerChestInventory = containerHandler.getInventory();
                        event.containerName = Util.removeFormatting(genericContainerScreen.getTitle().getString());
                        event.itemStacks = returnItemStacks(event.lowerChestInventory);

                        // Post to custom event bus
                        BazaarUtils.eventBus.post(new ChestLoadedEvent());
                        Util.notifyAll("Chest Loaded Event posted!");
                    }
                });
            }
        });
    }

    private static List<ItemStack> returnItemStacks(Inventory inventory) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static void checkIfGuiLoaded(GenericContainerScreen screen) {
        while (true) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            ScreenHandler handler = screen.getScreenHandler();
            if (handler instanceof GenericContainerScreenHandler containerHandler) {
                Inventory inv = containerHandler.getInventory();
                int size = inv.size();
                if (size == 0) continue;

                ItemStack bottomRightItem = inv.getStack(size - 1);
                if (!bottomRightItem.isEmpty() && !isItemLoading(inv)) {
                    break;
                }
            }
        }
    }

    private static boolean isItemLoading(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack item = inventory.getStack(i);
            if (item.isEmpty()) continue;

            Text customName = item.get(DataComponentTypes.CUSTOM_NAME);
            if (customName != null) {
                String displayName = Util.removeFormatting(customName.getString());
                if (displayName.contains("Loading")) {
                    Util.notifyAll("Loading item...", Util.notificationTypes.GUI);
                    return true;
                }
            }
        }
        return false;
    }
    // Getters remain the same
    public Inventory getLowerChestInventory() {
        return lowerChestInventory;
    }

    public List<ItemStack> getItemStacks() {
        return itemStacks;
    }

    public String getContainerName() {
        return containerName;
    }

    public boolean inFlipMenu() {
        return containerName.contains("Order options");
    }

    @Override
    public void setCancelled(boolean b) {

    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}