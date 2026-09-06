package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

//used to change stack size String
@Mixin(GuiGraphicsExtractor.class)
public abstract class DrawContentMixin {
    @ModifyVariable(
            method = "itemCount",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private String modifyStackCountString(String text, Font textRenderer, ItemStack stack, int x, int y) {
        ItemStack effective = VisualItemAccessorKt.getVisualItem(stack);
        if (effective == null) effective = stack;
        String customData = effective.get(CustomDataComponents.CUSTOM_SIZE);

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