package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A square GUI sprite with a label beside it, the label centred against the sprite's height.
 *
 * <p>Non-interactive; it exists to be positioned and drawn, not clicked.
 */
public class LogoDisplayWidget extends AbstractWidget {
    private static final int GAP = 4;

    private final Identifier sprite;
    private final Component label;
    private final int spriteSize;

    public LogoDisplayWidget(int x, int y, int spriteSize, Identifier sprite, Component label) {
        super(x, y, spriteSize + GAP + Minecraft.getInstance().font.width(label), spriteSize, label);

        this.sprite = sprite;
        this.label = label;
        this.spriteSize = spriteSize;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), spriteSize, spriteSize);

        int textY = this.getY() + (spriteSize - textRenderer.lineHeight) / 2;

        // Shadowed: this draws over whatever screen is open, so it needs to stay legible on any background.
        context.drawString(textRenderer, label, this.getX() + spriteSize + GAP, textY, 0xFFFFFFFF, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
