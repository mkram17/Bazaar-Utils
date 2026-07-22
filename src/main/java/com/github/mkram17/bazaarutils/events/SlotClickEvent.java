package com.github.mkram17.bazaarutils.events;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.NotNull;

public class SlotClickEvent implements ICancellable {
    @NotNull
    public final AbstractContainerScreen<?> handledScreen;
    @NotNull
    public final Slot slot;
    public final int slotId;
    public int clickedButton;
    public ContainerInput clickType;
    public boolean usePickblockInstead = false;
    @Setter
    @Getter
    public boolean cancelled = false;

    public SlotClickEvent(AbstractContainerScreen<?> handledScreen, Slot slot, int slotId, int clickedButton, ContainerInput actionType) {
        this.handledScreen = handledScreen;
        this.slot = slot;
        this.slotId = slotId;
        this.clickedButton = clickedButton;
        this.clickType = actionType;
    }

    public void usePickblockInstead() {
        usePickblockInstead = true;
    }

}