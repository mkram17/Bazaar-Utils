package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import dev.isxander.yacl3.api.Option;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.Item;

public abstract class CustomItemButton {
    @Getter @Setter
    private boolean enabled;
    @Setter @Getter
    private int replaceSlotNumber;
    @Getter
    private final Item item;
    private boolean signClicked = false;

    public CustomItemButton(boolean enabled, int replaceSlotNumber, Item item) {
        this.enabled = enabled;
        this.replaceSlotNumber = replaceSlotNumber;
        this.item = item;
    }

    public abstract void onGUI(ReplaceItemEvent event);
    public abstract void onSlotClicked(SlotClickEvent event);

    public abstract Option<Boolean> createOption();
}
