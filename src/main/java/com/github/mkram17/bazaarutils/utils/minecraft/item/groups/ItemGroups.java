package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Set;

public final class ItemGroups {
    private ItemGroups() {}

    public static final Set<Item> GLASS_PANES = Set.of(
            Items.GLASS_PANE,
            Items.WHITE_STAINED_GLASS_PANE, Items.ORANGE_STAINED_GLASS_PANE,
            Items.MAGENTA_STAINED_GLASS_PANE, Items.LIGHT_BLUE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE, Items.LIME_STAINED_GLASS_PANE,
            Items.PINK_STAINED_GLASS_PANE, Items.GRAY_STAINED_GLASS_PANE,
            Items.LIGHT_GRAY_STAINED_GLASS_PANE, Items.CYAN_STAINED_GLASS_PANE,
            Items.PURPLE_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE,
            Items.BROWN_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
            Items.RED_STAINED_GLASS_PANE, Items.BLACK_STAINED_GLASS_PANE
    );

    public static final Set<Item> GLASS_BLOCKS = Set.of(
            Items.GLASS,
            Items.WHITE_STAINED_GLASS, Items.ORANGE_STAINED_GLASS,
            Items.MAGENTA_STAINED_GLASS, Items.LIGHT_BLUE_STAINED_GLASS,
            Items.YELLOW_STAINED_GLASS, Items.LIME_STAINED_GLASS,
            Items.PINK_STAINED_GLASS, Items.GRAY_STAINED_GLASS,
            Items.LIGHT_GRAY_STAINED_GLASS, Items.CYAN_STAINED_GLASS,
            Items.PURPLE_STAINED_GLASS, Items.BLUE_STAINED_GLASS,
            Items.BROWN_STAINED_GLASS, Items.GREEN_STAINED_GLASS,
            Items.RED_STAINED_GLASS, Items.BLACK_STAINED_GLASS
    );

    public static final StateItemGroup<Boolean> BOOKMARKED_STATE_GROUP = StateItemGroup.<Boolean>any()
            .on(true, Items.GREEN_STAINED_GLASS_PANE)
            .on(false, Items.RED_STAINED_GLASS_PANE)
            .build();
}