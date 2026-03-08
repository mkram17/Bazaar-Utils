package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;

public class ItemSlotButtonWidget extends TexturedButtonWidget {
    private final ItemStack itemStack;

    public ItemSlotButtonWidget(int x, int y, int width, int height, ButtonTextures textures, PressAction onPress, ItemStack itemStack, MutableText tooltip) {
        super(x, y, width, height, textures, onPress, net.minecraft.text.Text.empty());
        this.itemStack = itemStack;
        this.setTooltip(Tooltip.of(tooltip));
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        super.drawIcon(context, mouseX, mouseY, delta);

        if (this.itemStack != null && !this.itemStack.isEmpty()) {
            int itemX = this.getX() + (this.width - 16) / 2;
            int itemY = this.getY() + (this.height - 16) / 2;

            context.drawItem(this.itemStack, itemX, itemY);
        }
    }
}