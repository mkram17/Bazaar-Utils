package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.BazaarUtils;
import net.minecraft.resources.Identifier;

public interface SlotHighlight {
    /** The one sprite every highlight draws: a plain 16x16 backing, tinted per slot. */
    Identifier STANDARD_BACKGROUND = Identifier.tryBuild(BazaarUtils.MOD_ID, "highlights/standard_background");

    /** Override only for a highlight that needs a sprite other than the standard backing. */
    default Identifier getIdentifier() {
        return STANDARD_BACKGROUND;
    }

    Integer getHighlightColor(int slotIndex);
}
