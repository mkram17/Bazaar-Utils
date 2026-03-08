package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class TextDisplayWidget extends ClickableWidget {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final Text text;
    private final Alignment alignment;

    public TextDisplayWidget(int x, int y, int width, int height, Text text, Alignment alignment) {
        super(x, y, width, height, text);
        this.text = text;
        this.alignment = alignment;
    }

    public TextDisplayWidget(int x, int y, int width, int height, Text text) {
        this(x, y, width, height, text, Alignment.LEFT);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int textY = this.getY() + (this.height - textRenderer.fontHeight) / 2;
        int textX = switch (alignment) {
            case LEFT   -> this.getX();
            case CENTER -> this.getX() + (this.width - textRenderer.getWidth(text)) / 2;
            case RIGHT  -> this.getX() + this.width - textRenderer.getWidth(text);
        };

        context.drawText(textRenderer, text, textX, textY, 0xFFFFFFFF, false);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}