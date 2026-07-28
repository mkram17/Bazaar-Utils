package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.util.api.ItemElement;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ItemOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.types.ItemStringOptionWidget;
import com.github.mkram17.bazaarutils.config.util.client.components.options.ResetOptionWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsRepo;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigElementRenderer;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

        List<ItemStack> items = ItemsRepo.getItems(element.tag());

        ItemOptionWidget itemWidget = new ItemOptionWidget(items, entry::getString, entry::setString);

        ItemStringOptionWidget stringWidget = new ItemStringOptionWidget(
                entry::getString,
                s -> {
                    ItemStack resolved = ItemsRepo.resolve(s);
                    if (resolved == null) return false;

                    String resolvedId = ItemsRepo.identify(resolved);
                    if (items.stream().noneMatch(stack -> ItemsRepo.identify(stack).equals(resolvedId))) return false;

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