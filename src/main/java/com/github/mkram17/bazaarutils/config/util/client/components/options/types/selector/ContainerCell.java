package com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector;

import com.teamresourceful.resourcefulconfig.client.components.base.BaseWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public class ContainerCell extends BaseWidget {
    public static final int CELL_SIZE = 18;
    private static final Identifier SLOT_SPRITE = Identifier.ofVanilla("container/slot");

    private final ItemStack stack;

    private final boolean selected;

    private final Runnable onSelect;

    public ContainerCell(int x, int y, ItemStack stack, boolean selected, Runnable onSelect) {
        super(CELL_SIZE, CELL_SIZE);
        setPosition(x, y);
        this.stack    = stack;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, getX(), getY(), CELL_SIZE, CELL_SIZE);

        if (selected) {
            context.fill(getX() + 1, getY() + 1, getX() + CELL_SIZE - 1, getY() + CELL_SIZE - 1, 0x8800AA00);
        } else if (isHovered()) {
            context.fill(getX() + 1, getY() + 1, getX() + CELL_SIZE - 1, getY() + CELL_SIZE - 1, 0x80FFFFFF);
        }

        if (!stack.isEmpty()) {
            context.drawItem(stack, getX() + 1, getY() + 1);

            if (isHovered() && !stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT).hideTooltip()) {
                context.drawTooltip(
                        MinecraftClient.getInstance().textRenderer,
                        stack.getTooltip(Item.TooltipContext.DEFAULT, null, TooltipType.BASIC),
                        mouseX, mouseY
                );
            }
        }

        this.applyCursor(context);
    }

    @Override
    public void onClick(@NotNull Click event, boolean doubled) {
        onSelect.run();
    }
}