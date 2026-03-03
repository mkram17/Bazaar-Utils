package com.github.mkram17.bazaarutils.config.util.api;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ResourcefulConfigItems {
    private static Supplier<List<Item>> source = () -> Registries.ITEM.stream().toList();

    private static Predicate<Item> globalFilter = item -> true;

    private ResourcefulConfigItems() {}

    public static void setSource(Supplier<List<Item>> source) {
        ResourcefulConfigItems.source = source;
    }

    public static void addGlobalFilter(Predicate<Item> filter) {
        Predicate<Item> existing = ResourcefulConfigItems.globalFilter;
        ResourcefulConfigItems.globalFilter = item -> existing.test(item) && filter.test(item);
    }

    public static List<Item> getItems(String tag) {
        List<Item> base = source.get().stream().filter(globalFilter).toList();
        if (tag == null || tag.isEmpty()) return base;
        Identifier tagId = Identifier.tryParse(tag);
        if (tagId == null) return base;
        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
        return base.stream().filter(item -> item.getRegistryEntry().isIn(tagKey)).toList();
    }

    public static List<Item> getItems() {
        return getItems("");
    }
}