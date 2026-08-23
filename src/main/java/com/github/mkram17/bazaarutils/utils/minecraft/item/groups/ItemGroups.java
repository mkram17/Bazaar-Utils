package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class ItemGroups {
    private ItemGroups() {}

    public static final Set<ItemStackTemplate> GLASS_PANES = Set.of(
            new ItemStackTemplate(Items.GLASS_PANE),
            new ItemStackTemplate(Items.WHITE_STAINED_GLASS_PANE), new ItemStackTemplate(Items.ORANGE_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.MAGENTA_STAINED_GLASS_PANE), new ItemStackTemplate(Items.LIGHT_BLUE_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.YELLOW_STAINED_GLASS_PANE), new ItemStackTemplate(Items.LIME_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.PINK_STAINED_GLASS_PANE), new ItemStackTemplate(Items.GRAY_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.LIGHT_GRAY_STAINED_GLASS_PANE), new ItemStackTemplate(Items.CYAN_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.PURPLE_STAINED_GLASS_PANE), new ItemStackTemplate(Items.BLUE_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.BROWN_STAINED_GLASS_PANE), new ItemStackTemplate(Items.GREEN_STAINED_GLASS_PANE),
            new ItemStackTemplate(Items.RED_STAINED_GLASS_PANE), new ItemStackTemplate(Items.BLACK_STAINED_GLASS_PANE)
    );

    public static final Set<ItemStackTemplate> GLASS_BLOCKS = Set.of(
            new ItemStackTemplate(Items.GLASS),
            new ItemStackTemplate(Items.WHITE_STAINED_GLASS), new ItemStackTemplate(Items.ORANGE_STAINED_GLASS),
            new ItemStackTemplate(Items.MAGENTA_STAINED_GLASS), new ItemStackTemplate(Items.LIGHT_BLUE_STAINED_GLASS),
            new ItemStackTemplate(Items.YELLOW_STAINED_GLASS), new ItemStackTemplate(Items.LIME_STAINED_GLASS),
            new ItemStackTemplate(Items.PINK_STAINED_GLASS), new ItemStackTemplate(Items.GRAY_STAINED_GLASS),
            new ItemStackTemplate(Items.LIGHT_GRAY_STAINED_GLASS), new ItemStackTemplate(Items.CYAN_STAINED_GLASS),
            new ItemStackTemplate(Items.PURPLE_STAINED_GLASS), new ItemStackTemplate(Items.BLUE_STAINED_GLASS),
            new ItemStackTemplate(Items.BROWN_STAINED_GLASS), new ItemStackTemplate(Items.GREEN_STAINED_GLASS),
            new ItemStackTemplate(Items.RED_STAINED_GLASS), new ItemStackTemplate(Items.BLACK_STAINED_GLASS)
    );

    public static final StateItemGroup<Boolean> BOOKMARKED_STATE_GROUP = StateItemGroup.<Boolean>any()
            .on(true, new ItemStackTemplate(Items.GREEN_STAINED_GLASS_PANE))
            .on(false, new ItemStackTemplate(Items.RED_STAINED_GLASS_PANE))
            .build();
}