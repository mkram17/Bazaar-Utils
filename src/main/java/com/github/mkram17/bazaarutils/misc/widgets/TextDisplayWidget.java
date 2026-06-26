package com.github.mkram17.bazaarutils.misc.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class TextDisplayWidget extends AbstractWidget {
    private final Component text;

    public TextDisplayWidget(int x, int y, int width, int height, Component text) {
        super(x, y, width, height, text);
        this.text = text;
    }

    @Override
    protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;
        int textWidth = textRenderer.width(text);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - textRenderer.lineHeight) / 2;
        context.text(textRenderer, text, textX, textY, 0xFFFFFF, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }
}
