package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.client.components.options.AbstractSelectorOverlay;
import com.github.mkram17.bazaarutils.config.util.client.components.options.SelectorOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.ContainerCell;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.ItemCell;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.options.text.TextBox;
import com.teamresourceful.resourcefulconfig.client.utils.ListenableState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemOptionWidget extends SelectorOptionWidget {
    protected static final Text SEARCH = Text.translatable("bazaarutils.rconfig.ui.constant.search");

    private final List<Item> items;
    private final Supplier<String> getter;
    private final Consumer<String> setter;

    public ItemOptionWidget(List<Item> items, Supplier<String> getter, Consumer<String> setter) {
        super(ModSprites.BUTTON, SELECT);
        this.items = items;
        this.getter = getter;
        this.setter = setter;
    }

    private @Nullable Item resolveItem() {
        Identifier id = Identifier.tryParse(getter.get());

        if (id == null) return null;

        return items.stream()
                .filter(item -> Registries.ITEM.getId(item).equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        super.drawIcon(context, mouseX, mouseY, delta);

        Item item = resolveItem();

        if (item != null) {
            context.drawItem(new ItemStack(item), getX(), getY());
        }
    }

    @Override
    public void onPress(@NotNull AbstractInput modifiers) {
        MinecraftClient.getInstance().setScreen(new ItemSelector(this));
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

        private final List<Item> allItems;
        private List<Item> filteredItems;

        private int scrollOffset = 0;

        private final List<ClickableWidget> cellWidgets = new ArrayList<>();

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
            cellWidgets.forEach(this::remove);
            cellWidgets.clear();

            scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll());
            oh = overlayHeight();

            int startX = ox + PADDING;
            int startY = oy + PADDING + SEARCH_HEIGHT + SPACING;
            int startIndex = scrollOffset * GRID_COLS;

            for (int i = 0; i < MAX_ROWS * GRID_COLS; i++) {
                int itemIndex = startIndex + i;
                if (itemIndex >= filteredItems.size()) break;

                Item item = filteredItems.get(itemIndex);
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;

                ItemCell cell = new ItemCell(
                        startX + col * ContainerCell.CELL_SIZE,
                        startY + row * ContainerCell.CELL_SIZE,
                        item,
                        selected -> {
                            this.setter.accept(Registries.ITEM.getId(selected).toString());
                            close();
                        }
                );
                cellWidgets.add(cell);
                addDrawableChild(cell);
            }
        }

        private void applySearch(String query) {
            String q = query.toLowerCase().trim();
            scrollOffset = 0;

            filteredItems = allItems.stream().filter(item -> {
                if (q.isEmpty()) return true;

                String name = item.getName(new ItemStack(item)).getString().toLowerCase();
                String key = Registries.ITEM.getId(item).toString().toLowerCase();

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
            ox = MathHelper.clamp(centerX - ow / 2, 0, this.width - ow);

            ListenableState<String> searchState = ListenableState.of("");
            searchState.registerListener(q -> {
                applySearch(q);
                rebuildCells();
            });

            this.searchBox = new TextBox(ow - PADDING * 2, SEARCH_HEIGHT, searchState) {
                @Override
                public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ModSprites.BUTTON, getX(), getY(), getWidth(), getHeight());
                    super.renderWidget(context, mouseX, mouseY, delta);
                    this.applyCursor(context);
                }
            };
            this.searchBox.setPosition(ox + PADDING, oy + PADDING);
            this.searchBox.setPlaceholder(SEARCH.getString(), 0xFF808080);
            addDrawableChild(this.searchBox);

            rebuildCells();
        }

        @Override
        public void renderBackground(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
            super.renderBackground(context, mouseX, mouseY, delta);

            if (maxScroll() > 0) {
                int trackTop = oy + PADDING + SEARCH_HEIGHT + SPACING;
                int trackHeight = visibleRows() * ContainerCell.CELL_SIZE;
                int thumbHeight = Math.max(6, trackHeight * MAX_ROWS / totalRows());
                int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll());
                context.fill(ox + ow - 3, trackTop, ox + ow - 1, trackTop + trackHeight, 0x44FFFFFF);
                context.fill(ox + ow - 3, thumbTop, ox + ow - 1, thumbTop + thumbHeight, 0xAAFFFFFF);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isOverOverlay(mouseX, mouseY)) return false;

            int newOffset = MathHelper.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());

            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildCells();
            }

            return true;
        }

        @Override
        public boolean charTyped(CharInput input) {
            if (searchBox != null && !searchBox.isFocused()) {
                setInitialFocus(searchBox);
                return searchBox.charTyped(input);
            }

            return super.charTyped(input);
        }
    }
}