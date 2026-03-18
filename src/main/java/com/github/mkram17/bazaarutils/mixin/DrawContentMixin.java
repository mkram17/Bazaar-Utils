package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//used to change stack size String
@Mixin(DrawContext.class)
public abstract class DrawContentMixin {
    @ModifyVariable(
            method = "drawStackCount",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private String modifyStackCountString(String text, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        String customData = stack.get(CustomDataComponents.CUSTOM_SIZE);

        double dataSize;
        if (customData != null) {
            boolean hasNumber = customData.matches(".*\\d.*");

            if(hasNumber)
                dataSize = Double.parseDouble(customData);
            else
                return customData;

            if(dataSize >= 1_000_000)
                return (((int) dataSize) / 1_000_000) + "m";
            if(dataSize >= 1_000)
                return (((int) dataSize) / 1_000) + "k";
            return customData;
        }

        return text;
    }
}