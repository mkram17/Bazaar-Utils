package com.github.sirmegabite.bazaarutils.EventHandlers;

import net.minecraft.inventory.IInventory;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraft.inventory.ContainerChest;

public class ChestLoadedEvent extends Event {
    private final IInventory lowerChestInventory;

    public ChestLoadedEvent(IInventory lowerChestInventory) {
        this.lowerChestInventory = lowerChestInventory;
    }

    public IInventory getLowerChestInventory() {
        return lowerChestInventory;
    }
}