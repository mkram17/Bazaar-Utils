package com.github.mkram17.bazaarutils.utils.minecraft;

import net.minecraft.util.Identifier;

public interface SlotHighlight {
    Identifier getIdentifier();

    Integer getHighlightColor(int slotIndex);
}