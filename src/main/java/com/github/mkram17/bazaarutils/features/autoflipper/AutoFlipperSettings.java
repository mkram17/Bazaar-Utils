package com.github.mkram17.bazaarutils.features.autoflipper;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.Item;

public class AutoFlipperSettings {
    @Getter
    @Setter
    private boolean enabled;
    @Getter
    @Setter
    private int slotNumber;
    @Getter
    private Item replaceItem;

    public AutoFlipperSettings(boolean enabled, int slotNumber, Item item) {
        this.enabled = enabled;
        this.slotNumber = slotNumber;
        this.replaceItem = item;
    }
}
