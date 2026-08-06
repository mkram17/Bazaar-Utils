package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class TextDisplayWidget extends AbstractWidget {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final Supplier<Component> text;
    private final Alignment alignment;

    /**
     * Resolves its text every frame. {@link WidgetManager} only rebuilds widgets on
     * {@code ContainerLoadedEvent} / {@code ScreenChangeEvent.Pre}, so a widget built from a fixed
     * {@link Component} freezes for as long as the screen stays open — use this constructor when the
     * text is derived from data that updates underneath an open screen.
     */
    public TextDisplayWidget(int x, int y, int width, int height, Supplier<Component> text, Alignment alignment) {
        super(x, y, width, height, text.get());
        this.text = text;
        this.alignment = alignment;
    }

    public TextDisplayWidget(int x, int y, int width, int height, Component text, Alignment alignment) {
        this(x, y, width, height, () -> text, alignment);
    }

    public TextDisplayWidget(int x, int y, int width, int height, Component text) {
        this(x, y, width, height, text, Alignment.LEFT);
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;
        Component current = text.get();

        int textY = this.getY() + (this.height - textRenderer.lineHeight) / 2;
        int textX = switch (alignment) {
            case LEFT   -> this.getX();
            case CENTER -> this.getX() + (this.width - textRenderer.width(current)) / 2;
            case RIGHT  -> this.getX() + this.width - textRenderer.width(current);
        };

        context.drawString(textRenderer, current, textX, textY, 0xFFFFFFFF, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
