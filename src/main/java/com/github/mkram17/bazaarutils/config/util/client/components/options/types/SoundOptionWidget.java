package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.client.components.options.AbstractSelectorOverlay;
import com.github.mkram17.bazaarutils.config.util.client.components.options.SelectorOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector.SoundCell;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundsRepo;
import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.options.text.TextBox;
import com.teamresourceful.resourcefulconfig.client.utils.ListenableState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SoundOptionWidget extends SelectorOptionWidget {
    protected static final Component SEARCH = Component.translatable("bazaarutils.rconfig.ui.constant.search");
    protected static final Component NO_RESULTS = Component.translatable("bazaarutils.rconfig.ui.constant.no_results");

    private final List<SoundEvent> sounds;
    private final Supplier<String> getter;
    private final Consumer<String> setter;

    public SoundOptionWidget(List<SoundEvent> sounds, Supplier<String> getter, Consumer<String> setter) {
        super(ModSprites.EDIT, UIConstants.EDIT);
        this.sounds = sounds;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress(@NotNull InputWithModifiers modifiers) {
        Minecraft.getInstance().setScreen(new SoundSelector(this));
    }

    public static class SoundSelector extends AbstractSelectorOverlay {
        private final SoundOptionWidget source;

        private static final int PADDING = 4;
        private static final int SPACING = 2;

        private static final int ROW_HEIGHT = 14;
        private static final int MAX_ROWS = 8;
        private static final int OVERLAY_WIDTH = 180;

        private static final int SEARCH_HEIGHT = 14;

        private final Consumer<String> setter;

        private final List<SoundEvent> allSounds;
        private List<SoundEvent> filteredSounds;

        private int scrollOffset = 0;

        private final List<AbstractWidget> rowWidgets = new ArrayList<>();

        private TextBox searchBox;

        public SoundSelector(SoundOptionWidget source) {
            this.source = source;
            this.setter = source.setter;
            this.allSounds = source.sounds;
            this.filteredSounds = new ArrayList<>(allSounds);
        }

        private int maxScroll() {
            return Math.max(0, filteredSounds.size() - MAX_ROWS);
        }

        private int visibleRows() {
            return Math.min(MAX_ROWS, filteredSounds.size());
        }

        private int overlayHeight() {
            return PADDING * 2 + SEARCH_HEIGHT + SPACING + MAX_ROWS * ROW_HEIGHT;
        }

        private void rebuildRows() {
            rowWidgets.forEach(this::removeWidget);
            rowWidgets.clear();

            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
            oh = overlayHeight();

            int startX = ox + PADDING;
            int startY = oy + PADDING + SEARCH_HEIGHT + SPACING;
            int rowWidth = ow - PADDING * 2;

            String current = source.getter.get();

            for (int i = 0; i < MAX_ROWS; i++) {
                int index = scrollOffset + i;
                if (index >= filteredSounds.size()) break;

                SoundEvent sound = filteredSounds.get(index);
                boolean selected = SoundsRepo.identify(sound).equals(current);

                SoundCell cell = new SoundCell(
                        startX, startY + i * ROW_HEIGHT,
                        rowWidth, ROW_HEIGHT,
                        sound, selected,
                        () -> {
                            this.setter.accept(SoundsRepo.identify(sound));
                            onClose();
                        }
                );

                rowWidgets.add(cell);

                addRenderableWidget(cell);
            }
        }

        private void applySearch(String query) {
            String q = query.toLowerCase().trim();
            scrollOffset = 0;

            filteredSounds = allSounds.stream()
                    .filter(sound -> q.isEmpty() || SoundsRepo.identify(sound).toLowerCase().contains(q))
                    .toList();
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
                rebuildRows();
            });

            this.searchBox = new TextBox(ow - PADDING * 2, SEARCH_HEIGHT, searchState) {
                @Override
                public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.BUTTON, getX(), getY(), getWidth(), getHeight());
                    super.renderWidget(context, mouseX, mouseY, delta);
                    this.applyCursor(context);
                }
            };
            this.searchBox.setPosition(ox + PADDING, oy + PADDING);
            this.searchBox.setPlaceholder(SEARCH.getString(), 0xFF808080);
            addRenderableWidget(this.searchBox);

            rebuildRows();
        }

        @Override
        public void renderBackground(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderBackground(context, mouseX, mouseY, delta);

            if (filteredSounds.isEmpty()) {
                Font font = Minecraft.getInstance().font;
                int textY = oy + PADDING + SEARCH_HEIGHT + SPACING + (MAX_ROWS * ROW_HEIGHT - font.lineHeight) / 2;
                context.drawCenteredString(font, NO_RESULTS, ox + ow / 2, textY, 0xFF808080);
                return;
            }

            if (maxScroll() > 0) {
                int trackTop = oy + PADDING + SEARCH_HEIGHT + SPACING;
                int trackHeight = visibleRows() * ROW_HEIGHT;
                int thumbHeight = Math.max(6, trackHeight * MAX_ROWS / filteredSounds.size());
                int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll());
                context.fill(ox + ow - 3, trackTop, ox + ow - 1, trackTop + trackHeight, 0x44FFFFFF);
                context.fill(ox + ow - 3, thumbTop, ox + ow - 1, thumbTop + thumbHeight, 0xAAFFFFFF);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isOverOverlay(mouseX, mouseY)) return false;

            int newOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());

            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildRows();
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