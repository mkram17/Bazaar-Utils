package com.github.mkram17.bazaarutils.ui.widgets;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.screen.ScreenTexts;

public class ItemSlotButtonWidget extends TexturedButtonWidget {
    private final ItemStack itemStack;
    public record ScreenWidgetDimensions(int x, int y, int backgroundWidth) {}

    public ItemSlotButtonWidget(int x, int y, int width, int height, ButtonTextures textures, PressAction onPress, ItemStack itemStack, MutableText tooltip) {
        super(x, y, width, height, textures, onPress, ScreenTexts.EMPTY);
        this.itemStack = itemStack;
        this.setTooltip(Tooltip.of(tooltip));
    }

    @Override
//? if 1.21.11 {
/*public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
    super.drawIcon(context, mouseX, mouseY, delta);
    int itemX = this.getX() + (this.width - 16) / 2;
    int itemY = this.getY() + (this.height - 16) / 2;
*///? } else {
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        int itemX = this.getX() + (this.width - 16) / 2;
        int itemY = this.getY() + (this.height - 16) / 2;
//? }
        if (this.itemStack != null && !this.itemStack.isEmpty()) {
            context.drawItem(this.itemStack, itemX, itemY);
        }
    }

    public static ScreenWidgetDimensions getSafeScreenDimensions(AccessorHandledScreen screen, String screenTitle) {
        int currentX = screen.getX();
        int currentY = screen.getY();
        int currentBgWidth = screen.getBackgroundWidth();

        if (currentBgWidth <= 0) {
            PlayerActionUtil.notifyAll("BackgroundWidth is not yet initialized correctly in init TAIL for " + screenTitle, NotificationType.GUI);
        }

        return new ScreenWidgetDimensions(currentX, currentY, currentBgWidth);
    }
}