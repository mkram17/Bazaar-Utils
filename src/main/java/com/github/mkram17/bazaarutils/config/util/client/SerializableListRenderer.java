package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.RCInternals;
import com.github.mkram17.bazaarutils.config.util.api.SerializableList;
import com.github.mkram17.bazaarutils.config.util.api.SerializableListElement;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ListOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ResetOptionWidget;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigElementRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Pure UI renderer for {@link SerializableList} fields.
 *
 * <p>All metadata and save-wiring is delegated to {@link SerializableListElement}.
 * Register in your {@code ClientModInitializer}:
 * <pre>
 *   ResourcefulConfigUI.registerElementRenderer(
 *       Identifier.of("yourmod", "serializable_list"),
 *       element -> {
 *           SerializableListElement sle = SerializableListElement.wrap(element, YourConfig.INSTANCE);
 *           return sle != null ? new SerializableListRenderer(sle) : null;
 *       }
 *   );
 * </pre>
 */
public record SerializableListRenderer(
        SerializableListElement element
) implements ResourcefulConfigElementRenderer {

    @Override
    public Text title() {
        return element.title();
    }

    @Override
    public Text description() {
        return element.description();
    }

    @Override
    public List<ClickableWidget> widgets() {
        SerializableList<?> list = element.list();
        list.setSaveCallback(() -> RCInternals.writeConfig(element.config()));

        return List.of(
                new ListOptionWidget<>(element.entry(), list),
                ResetOptionWidget.of(() -> {
                    list.entries.clear();
                    list.requestSave();
                })
        );
    }
}