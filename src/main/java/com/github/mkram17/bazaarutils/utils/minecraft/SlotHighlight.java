package com.github.mkram17.bazaarutils.utils.minecraft;

import net.minecraft.resources.Identifier;

public interface SlotHighlight {
    Identifier getIdentifier();

    Integer getHighlightColor(int slotIndex);
}