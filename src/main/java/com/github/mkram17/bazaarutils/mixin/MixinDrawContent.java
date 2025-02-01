package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//
@Mixin(DrawContext.class)
public abstract class MixinDrawContent {
//    @ModifyVariable(method = "drawStackCount", at = @At("STORE"), ordinal = 0)
//    private String changeStack(String value){
//        int num = Integer.valueOf(value);
//            if(value == "71680")
//                return "MAX";
//            else if (num >= 1000) {
//                return (num / 1000) + "k";
//            }else if (num >= 1000) {
//                return (num / 1000) + "k";
//            }
//        return value;
//    }
    @Inject(
            method = "drawStackCount",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I"),
            cancellable = true
    )
    private void modifyStackCount(
            TextRenderer textRenderer, ItemStack stack, int x, int y, String stackCountText, CallbackInfo ci
    ) {
        if (stack.getCount() == 71680) {
            // Replicate the rendering logic but replace the string
            ((DrawContext) (Object) this).drawText(
                    textRenderer,
                    "MAX",
                    x + 19 - 2 - textRenderer.getWidth("MAX"),
                    y + 6 + 3,
                    0xFFFFFF, // White color (RGB: 16777215)
                    true // Shadow
            );
        }else if (stack.getCount() >= 1000) {
            ((DrawContext) (Object) this).drawText(
                    textRenderer,
                    (stack.getCount() / 1000) + "k",
                    x + 19 - 2 - textRenderer.getWidth("MAX"),
                    y + 6 + 3,
                    0xFFFFFF, // White color (RGB: 16777215)
                    true // Shadow
            );
        }
        ci.cancel(); // Cancel the original call

    }

}