package com.github.mkram17.bazaarutils.features.customorder;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.Item;

public class CustomOrderSettings {
    @Getter @Setter
    private boolean enabled;
    @Getter @Setter
    private int orderAmount;
    @Getter @Setter
    private int slotNumber;
    @Getter
    private Item item;

    public CustomOrderSettings(boolean enabled, int orderAmount, int slotNumber, Item item) {
        this.enabled = enabled;
        this.orderAmount = orderAmount;
        this.slotNumber = slotNumber;
        this.item = item;
    }
}
