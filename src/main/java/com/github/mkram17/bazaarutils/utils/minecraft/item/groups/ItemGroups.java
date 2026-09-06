package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ColorCollection;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ItemGroups {
    private ItemGroups() {}

    public static final Set<ItemStackTemplate> GLASS_PANES = withColors(Items.GLASS_PANE, Items.STAINED_GLASS_PANE);

    public static final Set<ItemStackTemplate> GLASS_BLOCKS = withColors(Items.GLASS, Items.STAINED_GLASS);

    public static final StateItemGroup<Boolean> BOOKMARKED_STATE_GROUP = StateItemGroup.<Boolean>any()
            .on(true, new ItemStackTemplate(Items.STAINED_GLASS_PANE.green()))
            .on(false, new ItemStackTemplate(Items.STAINED_GLASS_PANE.red()))
            .build();

    /** The uncoloured item plus all 16 dyed variants, as templates. */
    private static Set<ItemStackTemplate> withColors(Item plain, ColorCollection<Item> dyed) {
        return Stream.concat(Stream.of(plain), dyed.asList().stream())
                .map(ItemStackTemplate::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
