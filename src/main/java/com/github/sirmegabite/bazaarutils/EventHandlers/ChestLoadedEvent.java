package com.github.sirmegabite.bazaarutils.EventHandlers;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraft.inventory.ContainerChest;

import java.util.ArrayList;
import java.util.List;

public class ChestLoadedEvent extends Event {
    private final IInventory lowerChestInventory;
    private List<ItemStack> itemStacks = new ArrayList<>();
    private final String containerName;

    public ChestLoadedEvent(IInventory lowerChestInventory) {
        this.lowerChestInventory = lowerChestInventory;
        containerName = lowerChestInventory.getDisplayName().getFormattedText();
        load();
    }

    private void load(){
        updateItemStacks();
    }
    private void updateItemStacks() {
        itemStacks.clear();
        for (int i = 0; i < lowerChestInventory.getSizeInventory(); i++) {
            ItemStack stack = lowerChestInventory.getStackInSlot(i);

            if (stack != null) {
                itemStacks.add(stack);
            }
        }
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