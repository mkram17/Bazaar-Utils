package com.github.mkram17.bazaarutils.misc.widgets;

import com.github.mkram17.bazaarutils.mixin.AccessorAbstractContainerScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.MutableComponent;

public class ItemSlotButtonWidget extends ImageButton {
    private final ItemStack itemStack;
    public record ScreenWidgetDimensions(int x, int y, int backgroundWidth) {}

    public ItemSlotButtonWidget(int x, int y, int width, int height, WidgetSprites textures, OnPress onPress, ItemStack itemStack, MutableComponent tooltip) {
        super(x, y, width, height, textures, onPress, net.minecraft.network.chat.Component.empty());
        this.itemStack = itemStack;
        this.setTooltip(Tooltip.create(tooltip));
    }
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractContents(context, mouseX, mouseY, delta);

        if (this.itemStack != null && !this.itemStack.isEmpty()) {
            int itemX = this.getX() + (this.width - 16) / 2;
            int itemY = this.getY() + (this.height - 16) / 2;

            context.item(this.itemStack, itemX, itemY);
        }
    }
    public static ScreenWidgetDimensions getSafeScreenDimensions(AccessorAbstractContainerScreen screen, String screenTitle) {
        int currentX = screen.getLeftPos();
        int currentY = screen.getTopPos();
        int currentBgWidth = screen.getImageWidth();

        if (currentBgWidth <= 0) {
            PlayerActionUtil.notifyAll("BackgroundWidth is not yet initialized correctly in init TAIL for " + screenTitle, Util.notificationTypes.GUI);
        }
        return new ScreenWidgetDimensions(currentX, currentY, currentBgWidth);
    }
}