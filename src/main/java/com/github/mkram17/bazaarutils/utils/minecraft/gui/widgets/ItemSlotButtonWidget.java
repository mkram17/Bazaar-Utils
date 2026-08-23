package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NonNull;

public class ItemSlotButtonWidget extends ImageButton {
    private final ItemStack itemStack;

    public ItemSlotButtonWidget(int x, int y, int width, int height, WidgetSprites textures, OnPress onPress, ItemStack itemStack, MutableComponent tooltip) {
        super(x, y, width, height, textures, onPress, net.minecraft.network.chat.Component.empty());
        this.itemStack = itemStack;
        this.setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractContents(graphics, mouseX, mouseY, delta);

        if (this.itemStack != null && !this.itemStack.isEmpty()) {
            int itemX = this.getX() + (this.width - 16) / 2;
            int itemY = this.getY() + (this.height - 16) / 2;

            graphics.item(this.itemStack, itemX, itemY);
        }
    }
}