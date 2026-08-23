package com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector;

import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.teamresourceful.resourcefulconfig.client.components.base.BaseWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class ContainerCell extends BaseWidget {
    public static final int CELL_SIZE = 18;
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    private final ItemStack stack;

    private final boolean selected;

    private final Runnable onSelect;

    public ContainerCell(int x, int y, ItemStack stack, boolean selected, Runnable onSelect) {
        super(CELL_SIZE, CELL_SIZE);
        setPosition(x, y);
        this.stack    = stack;
        this.selected = selected;
        this.active   = !(!stack.isEmpty() && stack.has(CustomDataComponents.SLOT_SELECTOR_LOCKED));
        this.onSelect = onSelect;
    }

    @Override
    protected void extractWidgetRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, getX(), getY(), CELL_SIZE, CELL_SIZE);

        if (selected) {
            graphics.fill(getX() + 1, getY() + 1, getX() + CELL_SIZE - 1, getY() + CELL_SIZE - 1, 0x8800AA00);
        } else if (isHovered()) {
            graphics.fill(getX() + 1, getY() + 1, getX() + CELL_SIZE - 1, getY() + CELL_SIZE - 1, 0x80FFFFFF);
        }

        if (!stack.isEmpty()) {
            graphics.item(stack, getX() + 1, getY() + 1);

            if (isHovered() && !stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()) {
                graphics.setComponentTooltipForNextFrame(
                        Minecraft.getInstance().font,
                        stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL),
                        mouseX, mouseY
                );
            }
        }

        this.applyCursor(graphics);
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean doubled) {
        onSelect.run();
    }
}