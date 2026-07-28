package com.github.mkram17.bazaarutils.config.util.client.components.options.types.selector;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class ItemCell extends ContainerCell {
    public ItemCell(int x, int y, ItemStack stack, Consumer<ItemStack> onSelect) {
        super(x, y, stack, false, () -> onSelect.accept(stack));
    }
}