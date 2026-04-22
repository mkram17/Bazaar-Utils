package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.repolib.api.RepoAPI;
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockCategory;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;
import tech.thatgravyboat.skyblockapi.api.remote.RepoItemsAPI;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class ItemsData {
    private static final BazaarLogger LOG = BazaarLogger.of(ItemsData.class);

    private static boolean SKYBLOCK_REPO_READY = false;
    private static final Map<String, ItemStack> RESOLVED_CACHE = new HashMap<>();
    private static volatile List<ItemStack> SKYBLOCK_ITEMS_CACHE = List.of();

    private ItemsData() {}

    public static void skyblockSourceReady() {
        if (SKYBLOCK_REPO_READY) return;
        else SKYBLOCK_REPO_READY = true;

        CompletableFuture.runAsync(() -> {
            SKYBLOCK_ITEMS_CACHE = buildSkyBlockItems();
            LOG.info("SkyBlock items cache built — {} items", SKYBLOCK_ITEMS_CACHE.size());
        });
    }

    public static @Nullable ItemStack resolve(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;

        return RESOLVED_CACHE.computeIfAbsent(rawId, id -> {
            if (SKYBLOCK_REPO_READY) {
                ItemStack result = RepoItemsAPI.INSTANCE.getItemOrNull(id);

                if (result != null) return result;
            }

            return resolveVanilla(id);
        });
    }

    /** Shim for call-sites that still need a bare {@link Item}. */
    public static @Nullable Item resolveItem(String rawId) {
        ItemStack stack = resolve(rawId);

        return stack != null ? stack.getItem() : null;
    }

    /**
     * Given a stack chosen in the picker, return the string ID to persist.
     * SkyBlock: bare Hypixel item ID ("HYPERION").
     * Vanilla: registry key ("minecraft:diamond").
     */
    public static String identify(ItemStack stack) {
        if (SKYBLOCK_REPO_READY) {
            String sbId = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getID());

            if (sbId != null) return sbId;
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static List<ItemStack> getItems(@Nullable String filter) {
        List<ItemStack> vanilla = getVanillaItems(filter);

        if (!SKYBLOCK_REPO_READY) return vanilla;

        return Stream.concat(vanilla.stream(), getSkyBlockItems(filter).stream()).toList();
    }

    public static List<ItemStack> getItems() {
        return getItems(null);
    }


    private static @Nullable ItemStack resolveVanilla(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;

        return BuiltInRegistries.ITEM.getOptional(identifier)
                .map(ItemStack::new)
                .orElse(null);
    }

    private static @Nullable ItemStack resolveSkyBlock(String id) {
        if (!RepoAPI.isInitialized()) return null;
        // RepoItemsAPI.getItemOrNull uppercases and handles ":" → "-" internally.
        return RepoItemsAPI.INSTANCE.getItemOrNull(id);
    }

    private static List<ItemStack> getVanillaItems(@Nullable String filter) {
        Stream<Item> items = BuiltInRegistries.ITEM.stream();

        if (filter != null && !filter.isEmpty()) {
            Identifier tagId = Identifier.tryParse(filter);
            if (tagId != null) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                items = items.filter(item -> BuiltInRegistries.ITEM.wrapAsHolder(item).is(tagKey));
            }
        }

        return items.map(ItemStack::new).toList();
    }

    private static List<ItemStack> getSkyBlockItems(@Nullable String filter) {
        List<ItemStack> cached = SKYBLOCK_ITEMS_CACHE;
        List<ItemStack> items = cached.isEmpty() ? buildSkyBlockItems() : cached;

        if (filter == null || filter.isEmpty()) return items;

        SkyBlockCategory category = SkyBlockCategory.Companion.create(filter);

        return items.stream()
                .filter(stack -> {
                    SkyBlockCategory stackCategory = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCATEGORY());

                    return stackCategory != null && category.equals(stackCategory, false);
                })
                .toList();
    }

    private static List<ItemStack> buildSkyBlockItems() {
        return RepoAPI.items().items().keySet().stream()
                .map(RepoItemsAPI.INSTANCE::getItemOrNull)
                .filter(Objects::nonNull)
                .toList();
    }
}