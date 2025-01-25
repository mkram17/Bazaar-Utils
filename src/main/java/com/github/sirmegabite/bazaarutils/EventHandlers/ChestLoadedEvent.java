package com.github.sirmegabite.bazaarutils.EventHandlers;

import com.github.sirmegabite.bazaarutils.Utils.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChestLoadedEvent extends Event {
    private IInventory lowerChestInventory;
    private List<ItemStack> itemStacks = new ArrayList<>();
    private String containerName;

    @SubscribeEvent
    public void onChestOpened(GuiOpenEvent e) {
        CompletableFuture.runAsync(this::checkIfGuiLoaded).thenRun(() -> {
            if (e.gui instanceof GuiChest) {
                Util.notifyAll("Chest loaded event went off!", Util.notificationTypes.GUI);

                // Create a *new* ChestLoadedEvent instance
                ChestLoadedEvent eventToPost = new ChestLoadedEvent();
                eventToPost.lowerChestInventory = ((ContainerChest) ((GuiChest) e.gui).inventorySlots).getLowerChestInventory();
                eventToPost.containerName = Util.removeFormatting(eventToPost.lowerChestInventory.getDisplayName().getFormattedText());
                eventToPost.itemStacks = returnItemStacks();

                MinecraftForge.EVENT_BUS.post(eventToPost); // Post the *new* event
            }
        });
    }

    private List<ItemStack> returnItemStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < lowerChestInventory.getSizeInventory(); i++) {
            ItemStack stack = lowerChestInventory.getStackInSlot(i);
            if (stack != null) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    public void checkIfGuiLoaded() {
        while (true) {
            //sometimes gui has not loaded at this point causing errors but wont say anything
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            GuiChest chestScreen = (GuiChest) screen;
            ContainerChest container = (ContainerChest) chestScreen.inventorySlots;
            int size = container.getLowerChestInventory().getSizeInventory();
            if (size == 0 || container.getLowerChestInventory() == null) {
                continue;
            }
            lowerChestInventory = container.getLowerChestInventory();
            itemStacks = returnItemStacks();
            ItemStack bottomRightItem = container.getLowerChestInventory().getStackInSlot(size - 1);
            if (bottomRightItem != null && !isItemLoading()) {
//                Util.notifyAll("Item detected in the bottom-right corner: " + bottomRightItem.getDisplayName(), Util.notificationTypes.GUI);
                break;
            }
        }
    }
    public boolean isItemLoading(){
        for (ItemStack item : itemStacks) {
            NBTTagCompound tagCompound = item.getTagCompound();
            if (tagCompound == null) continue;
            String displayName = Util.removeFormatting(tagCompound.getCompoundTag("display").getString("Name"));
            if(displayName.contains("Loading")) {
                Util.notifyAll("Loading item...", Util.notificationTypes.GUI);
                return true;
            }
        }
        return false;
    }

    public IInventory getLowerChestInventory() {
        return lowerChestInventory;
    }

    public List<ItemStack> getItemStacks() {
        return itemStacks;
    }

    public String getContainerName(){
        return containerName;
    }

    public boolean inFlipMenu(){
        return containerName.contains("Order options");
    }

}