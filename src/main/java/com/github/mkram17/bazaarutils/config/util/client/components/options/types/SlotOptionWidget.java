package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.github.mkram17.bazaarutils.config.util.client.components.options.AbstractSelectorOverlay;
import com.github.mkram17.bazaarutils.config.util.client.components.options.SelectorOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.ContainerCell;
import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SlotOptionWidget extends SelectorOptionWidget {
    private static final int SIZE = 12;

    private final SlotElement element;
    private final Supplier<Integer> getter;
    private final Consumer<Integer> setter;

    public SlotOptionWidget(SlotElement element, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(ModSprites.EDIT, UIConstants.EDIT);
        this.element = element;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress(@NotNull net.minecraft.client.input.AbstractInput modifiers) {
        MinecraftClient.getInstance().setScreen(new SlotSelector(this, element, getter.get(), setter));
    }

    public static class SlotSelector extends AbstractSelectorOverlay {
        private final SlotOptionWidget source;

        private static final int PADDING = 4;
        private static final int SPACING = 2;

        private final SlotElement element;

        private final Consumer<Integer> setter;

        private final int selectedSlot;

        private final List<ClickableWidget> cellWidgets = new ArrayList<>();

        public SlotSelector(SlotOptionWidget source, SlotElement element, int currentSlot, Consumer<Integer> setter) {
            this.source = source;
            this.element = element;
            this.setter = setter;
            this.selectedSlot = currentSlot;
        }

        private void rebuildCells() {
            cellWidgets.forEach(this::remove);
            cellWidgets.clear();

            int startX = ox + PADDING;
            int startY = oy + PADDING;

            for (int slot = 0; slot < element.totalSlots(); slot++) {
                final int s = slot;
                int col = slot % element.cols();
                int row = slot / element.cols();
                ItemStack stack = element.provider().getStack(slot, selectedSlot);

                ContainerCell cell = new ContainerCell(
                        startX + col * ContainerCell.CELL_SIZE,
                        startY + row * ContainerCell.CELL_SIZE,
                        stack,
                        slot == selectedSlot,
                        () -> {
                            setter.accept(s);
                            close();
                        }
                );
                cellWidgets.add(cell);
                addDrawableChild(cell);
            }
        }

        @Override
        protected void init() {
            ow = PADDING * 2 + element.cols() * ContainerCell.CELL_SIZE;
            oh = PADDING * 2 + element.rows() * ContainerCell.CELL_SIZE;

            oy = (source.getY() + source.getHeight() + SPACING + oh <= this.height)
                    ? source.getY() + source.getHeight() + SPACING
                    : source.getY() - oh - SPACING;

            int centerX = source.getX() + source.getWidth() / 2;
            ox = MathHelper.clamp(centerX - ow / 2, 0, this.width - ow);

            rebuildCells();
        }
    }
}