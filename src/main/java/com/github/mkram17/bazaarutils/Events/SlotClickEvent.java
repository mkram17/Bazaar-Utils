package com.github.mkram17.bazaarutils.Events;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.NotNull;

public class SlotClickEvent implements ICancellable {
    @NotNull
    public final HandledScreen<?> handledScreen;
    @NotNull
    public final Slot slot;
    public final int slotId;
    public int clickedButton;
    public SlotActionType clickType;
    public boolean usePickblockInstead = false;
    @Setter
    @Getter
    public boolean cancelled = false;

    public SlotClickEvent(HandledScreen<?> handledScreen, Slot slot, int slotId, int clickedButton, SlotActionType actionType) {
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