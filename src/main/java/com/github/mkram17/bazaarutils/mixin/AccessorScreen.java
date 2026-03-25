package com.github.mkram17.bazaarutils.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

//used for SlotClickEvent
@Mixin(Screen.class)
public interface AccessorScreen {
    @Accessor("minecraft")
    Minecraft getMinecraft();

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T registerWidget(T widget);

    @Invoker("removeWidget")
    void unregisterWidget(GuiEventListener element);

    @Accessor("renderables")
    List<Renderable> getRenderables();

    @Accessor("children")
    List<GuiEventListener> getChildren();
}