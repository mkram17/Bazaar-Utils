package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import dev.isxander.yacl3.api.Option;

public abstract class CustomItemButton {
    public abstract void onGUI(ReplaceItemEvent event);
    public abstract void onSlotClicked(SlotClickEvent event);

    public abstract Option<Boolean> createOption();
}
