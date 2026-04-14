package com.github.mkram17.bazaarutils.config.util.api;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ResourcefulConfigItems {
    private static final Map<String, Item> RESOLVED_CACHE = new HashMap<>();

    public static @Nullable Item resolve(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;

        return RESOLVED_CACHE.computeIfAbsent(rawId, id -> {
            Identifier identifier = Identifier.tryParse(id);

            if (identifier == null) return null;

            return source.get().stream()
                    .filter(item -> BuiltInRegistries.ITEM.getKey(item).equals(identifier))
                    .findFirst()
                    .orElse(null);
        });
    }


    private static Supplier<List<Item>> source = () -> BuiltInRegistries.ITEM.stream().toList();

    public static void setSource(Supplier<List<Item>> source) {
        ResourcefulConfigItems.source = source;
    }

    private static Predicate<Item> globalFilter = item -> true;

    public static void addGlobalFilter(Predicate<Item> filter) {
        Predicate<Item> existing = ResourcefulConfigItems.globalFilter;
        ResourcefulConfigItems.globalFilter = item -> existing.test(item) && filter.test(item);
    }


    private ResourcefulConfigItems() {}

    public static List<Item> getItems(String tag) {
        List<Item> base = source.get().stream().filter(globalFilter).toList();
        if (tag == null || tag.isEmpty()) return base;
        Identifier tagId = Identifier.tryParse(tag);
        if (tagId == null) return base;
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        return base.stream().filter(item -> item.builtInRegistryHolder().is(tagKey)).toList();
    }

    public static List<Item> getItems() {
        return getItems("");
    }
}