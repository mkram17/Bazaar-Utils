package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.RCInternals;
import com.github.mkram17.bazaarutils.config.util.api.SerializableList;
import com.github.mkram17.bazaarutils.config.util.api.SerializableListEntrySummaryProvider;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.EntryEditOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.RemoveOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ResetOptionWidget;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.options.OptionItem;
import com.teamresourceful.resourcefulconfig.client.components.options.OptionsListWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ObjectOptionWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen list manager for a {@link SerializableList}{@code <T>}.
 *
 * <p>Each entry row contains:
 * <ul>
 *   <li>An {@link ObjectOptionWidget} (Edit) — opens RC's native {@code ObjectEditOverlay}</li>
 *   <li>A {@link ResetOptionWidget} (Reset) — resets the entry's fields to class initializer defaults</li>
 *   <li>A remove button (✕)</li>
 * </ul>
 */
public class SerializableListScreen<T> extends Screen {
    public static final Text ADD_ENTRY = Text.translatable("bazaarutils.rconfig.ui.constant.add_entry");
    public static final Text TITLE = Text.translatable("bazaarutils.rconfig.ui.constant.manage_list");
    public static final Text DONE = Text.translatable("bazaarutils.rconfig.ui.constant.done");

    private static final int BUTTON_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 34;
    private static final int ADD_BUTTON_HEIGHT = 114;
    private static final int DONE_BUTTON_HEIGHT = 100;

    private final Screen parent;
    private final SerializableList<T> list;

    public SerializableListScreen(Screen parent, SerializableList<T> list) {
        super(TITLE);
        this.parent = parent;
        this.list   = list;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();

        int listWidth  = width - UIConstants.PAGE_PADDING * 2;
        int listHeight = height - HEADER_HEIGHT;

        OptionsListWidget optionsList = addDrawableChild(
                new OptionsListWidget(listWidth, listHeight)
        );

        optionsList.setPosition(UIConstants.PAGE_PADDING, HEADER_HEIGHT);

        for (int i = 0; i < list.entries.size(); i++) {
            T entry = list.entries.get(i);
            final int fi = i;

            ResourcefulConfigObjectEntry objectEntry =
                    RCInternals.buildObjectEntry(entry, list.newEntry());

            List<ClickableWidget> rowWidgets = new ArrayList<>();

            // Edit — RC's own ObjectOptionWidget, opens ObjectEditOverlay natively
            rowWidgets.add(objectEntry != null
                    ? new EntryEditOptionWidget(objectEntry, list::requestSave)
                    : ResourcefulConfigUI.button(0, 0, 100, BUTTON_HEIGHT, Text.literal("(parse error)"), () -> {})
            );

            // Reset — restores this entry's fields to class initializer defaults
            rowWidgets.add(ResetOptionWidget.of(() -> {
                RCInternals.resetEntryToDefaults(entry, list.newEntry());
                list.requestSave();
                rebuildWidgets();
            }));

            rowWidgets.add(RemoveOptionWidget.of(() -> {
                list.entries.remove(fi);
                list.requestSave();
                rebuildWidgets();
            }));

            optionsList.add(new OptionItem(
                    getSummary(entry, i),
                    getDescription(entry, i),
                    rowWidgets
            ));
        }

        int btnY = (HEADER_HEIGHT - BUTTON_HEIGHT) / 2;

        addDrawableChild(ResourcefulConfigUI.button(
                UIConstants.PAGE_PADDING, btnY,
                ADD_BUTTON_HEIGHT, BUTTON_HEIGHT,
                ADD_ENTRY,
                () -> {
                    list.entries.add(list.newEntry());
                    list.requestSave();
                    rebuildWidgets();
                }
        ));

        addDrawableChild(ResourcefulConfigUI.button(
                (width - DONE_BUTTON_HEIGHT) - UIConstants.PAGE_PADDING - 4, btnY,
                DONE_BUTTON_HEIGHT, BUTTON_HEIGHT,
                DONE,
                this::close
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UIConstants.BACKGROUND);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, UIConstants.TEXT_TITLE);
        String countText = list.entries.size() + " entr" + (list.entries.size() == 1 ? "y" : "ies");
        context.drawCenteredTextWithShadow(textRenderer, countText,
                width / 2, 8 + textRenderer.fontHeight + 2, UIConstants.TEXT_PARAGRAPH);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void close() {
        list.requestSave();
        client.setScreen(parent);
    }

    private static Text getSummary(Object entry, int index) {
        Text raw;

        if (entry instanceof SerializableListEntrySummaryProvider provider) {
            raw = provider.getSummary(index);
        } else {
            try {
                for (Field f : entry.getClass().getDeclaredFields()) {
                    if (f.isAnnotationPresent(ConfigEntry.class)) {
                        f.setAccessible(true);
                        Object value = f.get(entry);
                        if (value != null) {
                            return Text.literal(f.getAnnotation(ConfigEntry.class).id() + ": ")
                                    .append(Translatable.toComponent(value));
                        }
                    }
                }
            } catch (Exception ignored) {}

            raw = Text.literal("Entry #" + (index + 1));
        }
        return Text.empty().withColor(UIConstants.TEXT_TITLE).append(raw);
    }

    private static Text getDescription(Object entry, int index) {
        Text raw = entry instanceof SerializableListEntrySummaryProvider provider
                ? provider.getDescription(index)
                : Text.empty();

        return Text.empty().withColor(UIConstants.TEXT_PARAGRAPH).append(raw);
    }
}