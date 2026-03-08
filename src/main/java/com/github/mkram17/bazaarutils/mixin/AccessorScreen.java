package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

//used for SlotClickEvent
@Mixin(Screen.class)
public interface AccessorScreen {
    @Accessor("client")
    MinecraftClient getClient();

    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T bazaarutils$registerWidget(T widget);

    @Invoker("remove")
    void bazaarutils$unregisterWidget(Element element);

    @Accessor("drawables")
    List<Drawable> bazaarutils$getDrawables();

    @Accessor("children")
    List<Element> bazaarutils$getChildren();
}