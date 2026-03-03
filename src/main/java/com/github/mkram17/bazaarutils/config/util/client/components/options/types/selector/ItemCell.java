package com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

public class ItemCell extends ContainerCell {
    public ItemCell(int x, int y, Item item, Consumer<Item> onSelect) {
        super(x, y, new ItemStack(item), false, () -> onSelect.accept(item));
    }
}