package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.gui.inventory.GuiEditSign.class)
public class GuiEditSign {
    @Redirect(method = "onGuiClosed", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/tileentity/TileEntitySign;signText:[Lnet/minecraft/util/IChatComponent;"))
    public IChatComponent[] onOnGuiClosed(TileEntitySign instance) {
        String[] x = new String[4];
        for (int i = 0; i < 4; i++) {
            x[i] = instance.signText[i].getUnformattedText();
        }
        Event signSubmitEvent = new Event();
        MinecraftForge.EVENT_BUS.post(signSubmitEvent);
        IChatComponent[] arr = new IChatComponent[4];
        for (int i = 0; i < 4; i++) {
            arr[i] = new ChatComponentText(x[i]);
        }
        return arr;
    }
}
