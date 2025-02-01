package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//
@Mixin(DrawContext.class)
public abstract class DrawContentMixin {
    @ModifyVariable(
            method = "drawStackCount",
            at = @At("HEAD"),
            ordinal = 0
    )
    private String modifyStackCountString(String text, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        int size = stack.getCount();
        if (size == 71680)
            return "MAX";
        if (size >= 1000)
            return (size / 1000) + "k";

        return text;
    }

}