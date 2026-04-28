package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Sub-interface for modifiers whose only visual effect is colourizing the slot.
 * Implementors provide a single color via {@link #highlightColor} and declare
 * which layer to paint via {@link #getHighlightStyle()}.
 */
public interface SlotHighlight extends AbstractItemModifier {
    enum HighlightStyle implements Translatable {
        BACKGROUND,
        FOREGROUND,
        BORDER;

        @Override
        public String getTranslationKey() {
            return "bazaarutils.config.highlight.style." + name().toLowerCase() + ".label";
        }
    }

    @Override
    default ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.NAME_PREFIX;
    }

    Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot);

    HighlightStyle getHighlightStyle();

    @Override
    default Optional<Integer> backgroundColor(ItemStack stack, @Nullable Slot slot) {
        return getHighlightStyle() == HighlightStyle.BACKGROUND ? highlightColor(stack, slot) : Optional.empty();
    }

    @Override
    default Optional<Integer> foregroundColor(ItemStack stack, @Nullable Slot slot) {
        return getHighlightStyle() == HighlightStyle.FOREGROUND ? highlightColor(stack, slot) : Optional.empty();
    }

    @Override
    default Optional<Integer> borderColor(ItemStack stack, @Nullable Slot slot) {
        return getHighlightStyle() == HighlightStyle.BORDER ? highlightColor(stack, slot) : Optional.empty();
    }
}