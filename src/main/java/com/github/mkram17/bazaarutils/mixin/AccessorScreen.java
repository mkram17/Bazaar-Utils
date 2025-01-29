package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//used for SlotClickEvent
@Mixin(Screen.class)
public interface AccessorScreen {
    @Accessor("client")
    MinecraftClient getClient();
}