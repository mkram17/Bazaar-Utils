package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.ItemElement;
import com.github.mkram17.bazaarutils.config.util.api.ResourcefulConfigItems;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ItemOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ItemStringOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.ResetOptionWidget;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigElementRenderer;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ItemRenderer(ItemElement element) implements ResourcefulConfigElementRenderer {
    @Override
    public Component title() {
        return element.title();
    }

    @Override
    public Component description() {
        return element.description();
    }

    @Override
    public List<AbstractWidget> widgets() {
        ResourcefulConfigValueEntry entry = element.valueEntry();

        List<Item> items = ResourcefulConfigItems.getItems(element.tag());

        ItemOptionWidget itemWidget = new ItemOptionWidget(items, entry::getString, entry::setString);

        ItemStringOptionWidget stringWidget = new ItemStringOptionWidget(
                entry::getString,
                s -> {
                    Item resolved = ResourcefulConfigItems.resolve(s);

                    if (resolved == null || !items.contains(resolved)) return false;

                    entry.setString(s);
                    return true;
                }
        );

        return List.of(
                itemWidget,
                stringWidget,
                ResetOptionWidget.of(() -> {
                    entry.reset();
                    stringWidget.reset();
                })
        );
    }
}