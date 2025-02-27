package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
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
            ordinal = 0
    )
    private String modifyStackCountString(String text, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        String customData = stack.get(BazaarUtils.CUSTOM_SIZE_COMPONENT);
        double dataSize;
        if (customData != null) {
            boolean hasNumber = customData.matches(".*\\d.*");

            if(hasNumber)
                dataSize = Double.parseDouble(customData);
            else
                return customData;

            if(dataSize >= 1000000)
                return (((int) dataSize) / 1000000) + "m";
            if(dataSize >= 1000)
                return (((int) dataSize) / 1000) + "k";
            return customData;
        }

        return text;
    }

}