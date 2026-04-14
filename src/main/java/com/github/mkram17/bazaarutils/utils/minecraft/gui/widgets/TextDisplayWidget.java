package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class TextDisplayWidget extends AbstractWidget {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final Component text;
    private final Alignment alignment;

    public TextDisplayWidget(int x, int y, int width, int height, Component text, Alignment alignment) {
        super(x, y, width, height, text);
        this.text = text;
        this.alignment = alignment;
    }

    public TextDisplayWidget(int x, int y, int width, int height, Component text) {
        this(x, y, width, height, text, Alignment.LEFT);
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;

        int textY = this.getY() + (this.height - textRenderer.lineHeight) / 2;
        int textX = switch (alignment) {
            case LEFT   -> this.getX();
            case CENTER -> this.getX() + (this.width - textRenderer.width(text)) / 2;
            case RIGHT  -> this.getX() + this.width - textRenderer.width(text);
        };

        context.drawString(textRenderer, text, textX, textY, 0xFFFFFFFF, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}