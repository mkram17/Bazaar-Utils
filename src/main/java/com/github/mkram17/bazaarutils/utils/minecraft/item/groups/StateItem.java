package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import net.minecraft.world.item.Item;

public sealed interface StateItem {
    record Fixed(Item item) implements StateItem {}
    record Configured() implements StateItem {}

    static StateItem of(Item item) {
        return new Fixed(item);
    }

    static StateItem configured() {
        return new Configured();
    }
}