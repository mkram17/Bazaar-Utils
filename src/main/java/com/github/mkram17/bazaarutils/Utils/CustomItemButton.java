package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import dev.isxander.yacl3.api.Option;
import net.minecraft.item.Item;

import java.util.function.Supplier;

public abstract class CustomItemButton {
    private Supplier<Boolean> enabled;
    private final Supplier<Integer> replaceSlotNumber;
    private final Item item;
    private boolean signClicked = false;

    public CustomItemButton(Supplier<Boolean> enabled, Supplier<Integer> replaceSlotNumber, Item item) {
        this.enabled = enabled;
        this.replaceSlotNumber = replaceSlotNumber;
        this.item = item;
    }

    public boolean isEnabled() {
        return enabled.get();
    }
    public void setEnabled(boolean newValue){
        enabled = () -> newValue;
    }
    protected Item getButtonItem(){
        return this.item;
    }

    public int getReplaceSlotNumber() {
        return replaceSlotNumber.get();
    }

    public abstract void onGUI(ReplaceItemEvent event);
    public abstract void onSlotClicked(SlotClickEvent event);

    public abstract Option<Boolean> createOption();
}
