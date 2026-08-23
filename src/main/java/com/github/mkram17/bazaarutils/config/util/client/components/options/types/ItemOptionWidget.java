package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.client.components.options.AbstractSelectorOverlay;
import com.github.mkram17.bazaarutils.config.util.client.components.options.SelectorOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.ContainerCell;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.ItemCell;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsRepo;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.options.text.TextBox;
import com.teamresourceful.resourcefulconfig.client.utils.ListenableState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemOptionWidget extends SelectorOptionWidget {
    protected static final Component SEARCH = Component.translatable("bazaarutils.rconfig.ui.constant.search");

    private final List<ItemStack> items;
    private final Supplier<String> getter;
    private final Consumer<String> setter;

    private String lastResolvedId;
    private ItemStack lastResolvedStack;

    public ItemOptionWidget(List<ItemStack> items, Supplier<String> getter, Consumer<String> setter) {
        super(ModSprites.BUTTON, SELECT);
        this.items = items;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractContents(graphics, mouseX, mouseY, delta);

        String id = getter.get();
        if (!Objects.equals(id, lastResolvedId)) {
            lastResolvedId = id;
            lastResolvedStack = ItemsRepo.resolve(id);
        }

        if (lastResolvedStack != null) {
            graphics.item(lastResolvedStack, getX(), getY());
        }
    }

    @Override
    public void onPress(@NotNull InputWithModifiers modifiers) {
        Minecraft.getInstance().setScreen(new ItemSelector(this));
    }

    public static class ItemSelector extends AbstractSelectorOverlay {
        private final ItemOptionWidget source;

        private static final int PADDING = 4;
        private static final int SPACING = 2;

        private static final int GRID_COLS = 8;
        private static final int MAX_ROWS = 5;

        private static final int SEARCH_HEIGHT = 14;
        private static final int OVERLAY_WIDTH = GRID_COLS * ContainerCell.CELL_SIZE + PADDING * 2;

        private final Consumer<String> setter;

        private final List<ItemStack> allItems;
        private List<ItemStack> filteredItems;

        private int scrollOffset = 0;

        private final List<AbstractWidget> cellWidgets = new ArrayList<>();

        private TextBox searchBox;

        public ItemSelector(ItemOptionWidget source) {
            this.source = source;
            this.setter = source.setter;
            this.allItems = source.items;
            this.filteredItems = new ArrayList<>(allItems);
        }

        private int totalRows() {
            return (int) Math.ceil(filteredItems.size() / (double) GRID_COLS);
        }

        private int maxScroll() {
            return Math.max(0, totalRows() - MAX_ROWS);
        }

        private int visibleRows() {
            return Math.min(MAX_ROWS, totalRows());
        }

        private int overlayHeight() {
            return PADDING * 2 + SEARCH_HEIGHT + SPACING + MAX_ROWS * ContainerCell.CELL_SIZE;
        }

        private void rebuildCells() {
            cellWidgets.forEach(this::removeWidget);
            cellWidgets.clear();

            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
            oh = overlayHeight();

            int startX = ox + PADDING;
            int startY = oy + PADDING + SEARCH_HEIGHT + SPACING;
            int startIndex = scrollOffset * GRID_COLS;

            for (int i = 0; i < MAX_ROWS * GRID_COLS; i++) {
                int itemIndex = startIndex + i;
                if (itemIndex >= filteredItems.size()) break;

                ItemStack stack = filteredItems.get(itemIndex);
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;

                ItemCell cell = new ItemCell(
                        startX + col * ContainerCell.CELL_SIZE,
                        startY + row * ContainerCell.CELL_SIZE,
                        stack,
                        selected -> {
                            this.setter.accept(ItemsRepo.identify(selected));
                            onClose();
                        }
                );
                cellWidgets.add(cell);
                addRenderableWidget(cell);
            }
        }

        private void applySearch(String query) {
            String q = query.toLowerCase().trim();
            scrollOffset = 0;

            filteredItems = allItems.stream().filter(stack -> {
                if (q.isEmpty()) return true;

                String name = stack.getHoverName().getString().toLowerCase();
                String key = ItemsRepo.identify(stack).toLowerCase();

                return name.contains(q) || key.contains(q);
            }).toList();
        }

        @Override
        protected void init() {
            ow = OVERLAY_WIDTH;
            oh = overlayHeight();

            oy = (source.getY() + source.getHeight() + SPACING + oh <= this.height)
                    ? source.getY() + source.getHeight() + SPACING
                    : source.getY() - oh - SPACING;

            int centerX = source.getX() + source.getWidth() / 2;
            ox = Mth.clamp(centerX - ow / 2, 0, this.width - ow);

            ListenableState<String> searchState = ListenableState.of("");
            searchState.registerListener(q -> {
                applySearch(q);
                rebuildCells();
            });

            this.searchBox = new TextBox(ow - PADDING * 2, SEARCH_HEIGHT, searchState) {
                @Override
                public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.BUTTON, getX(), getY(), getWidth(), getHeight());
                    super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
                    this.applyCursor(graphics);
                }
            };
            this.searchBox.setPosition(ox + PADDING, oy + PADDING);
            this.searchBox.setPlaceholder(SEARCH.getString(), 0xFF808080);
            addRenderableWidget(this.searchBox);

            rebuildCells();
        }

        @Override
        public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractBackground(graphics, mouseX, mouseY, delta);

            if (maxScroll() > 0) {
                int trackTop = oy + PADDING + SEARCH_HEIGHT + SPACING;
                int trackHeight = visibleRows() * ContainerCell.CELL_SIZE;
                int thumbHeight = Math.max(6, trackHeight * MAX_ROWS / totalRows());
                int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll());
                graphics.fill(ox + ow - 3, trackTop, ox + ow - 1, trackTop + trackHeight, 0x44FFFFFF);
                graphics.fill(ox + ow - 3, thumbTop, ox + ow - 1, thumbTop + thumbHeight, 0xAAFFFFFF);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isOverOverlay(mouseX, mouseY)) return false;

            int newOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());

            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildCells();
            }

            return true;
        }

        @Override
        public boolean charTyped(CharacterEvent input) {
            if (searchBox != null && !searchBox.isFocused()) {
                setInitialFocus(searchBox);
                return searchBox.charTyped(input);
            }

            return super.charTyped(input);
        }
    }
}