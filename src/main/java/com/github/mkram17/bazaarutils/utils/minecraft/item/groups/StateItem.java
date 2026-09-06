package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import net.minecraft.world.item.ItemStackTemplate;

public sealed interface StateItem {
    record Fixed(ItemStackTemplate template) implements StateItem {}
    record Configured() implements StateItem {}

    static StateItem of(ItemStackTemplate template) {
        return new Fixed(template);
    }

    static StateItem configured() {
        return new Configured();
    }
}