package com.github.mkram17.bazaarutils.config.util.client.components.options;

import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.screens.base.OverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSelectorOverlay extends OverlayScreen {
    protected int ox, oy, ow, oh;

    protected AbstractSelectorOverlay() {
        super(Minecraft.getInstance().screen);
    }

    protected boolean isOverOverlay(double mouseX, double mouseY) {
        return mouseX >= ox && mouseX <= ox + ow && mouseY >= oy && mouseY <= oy + oh;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.ACCENT, ox, oy, ow, oh);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.BUTTON, ox + 1, oy + 1, ow - 2, oh - 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0 || isOverOverlay(click.x(), click.y())) {
            return super.mouseClicked(click, doubled);
        }

        onClose();

        return false;
    }
}