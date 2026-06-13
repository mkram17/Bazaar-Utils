package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//used to change stack size String
@Mixin(GuiGraphics.class)
public abstract class DrawContentMixin {
    @ModifyVariable(
            method = "renderItemCount",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private String modifyStackCountString(String text, Font textRenderer, ItemStack stack, int x, int y) {
        String customData = stack.get(BazaarUtils.CUSTOM_SIZE_COMPONENT);
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