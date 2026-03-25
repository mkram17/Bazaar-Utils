package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.github.mkram17.bazaarutils.config.util.client.components.options.ResetOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.SlotNumberOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.SlotOptionWidget;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigElementRenderer;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;

public record SlotRenderer(SlotElement element) implements ResourcefulConfigElementRenderer {
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
        ResourcefulConfigValueEntry entry = element.valueEntry();

        SlotOptionWidget slotWidget = new SlotOptionWidget(
                element,
                entry::getInt,
                entry::setInt
        );

        SlotNumberOptionWidget numberWidget = new SlotNumberOptionWidget(element);

        ClickableWidget resetWidget = ResetOptionWidget.of(() -> {
            entry.reset();
            numberWidget.reset();
        });

        return List.of(slotWidget, numberWidget, resetWidget);
    }
}