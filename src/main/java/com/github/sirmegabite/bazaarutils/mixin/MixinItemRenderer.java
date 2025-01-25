package com.github.sirmegabite.bazaarutils.mixin;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public class MixinItemRenderer {
    @Redirect(
            method = "renderItemOverlayIntoGUI",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"
            )
    )
    private String redirectStackSizeToString(int size) {
        // Only modify the stack size if there's no custom text (e.g., enchantment levels)
            int maxStackSize = 71680;

            if (size == maxStackSize) {
                return "MAX";
            } else if (size >= 1000) {
                return (size / 1000) + "k";
            }

        // Fallback to default behavior
        return String.valueOf(size);
    }
}
