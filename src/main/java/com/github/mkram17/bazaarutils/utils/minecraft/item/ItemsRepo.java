package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.resources.BazaarConversions;
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
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo;

import java.util.*;
import java.util.stream.Stream;

public final class ItemsRepo {
    private static final BazaarLogger LOG = BazaarLogger.of(ItemsRepo.class);

    private static final Map<String, ItemStack> RESOLVED_CACHE = new HashMap<>();
    private static List<ItemStack> SKYBLOCK_ITEMS_CACHE = List.of();
    private static boolean SKYBLOCK_REPO_READY = false;

    public ItemsRepo() {}

    /**
     * Typed representation of an {@code @ItemTag} value.
     */
    public sealed interface ItemFilter
            permits ItemFilter.All,
            ItemFilter.VanillaTag,
            ItemFilter.SkyBlockCategory,
            ItemFilter.SkyBlockBazaar {

        /** No filter — returns every vanilla + SkyBlock item. */
        record All() implements ItemFilter {}

        /**
         * Filters vanilla items by a Minecraft item tag.
         * Examples: {@code minecraft:logs}, {@code c:ores}
         */
        record VanillaTag(Identifier tagId) implements ItemFilter {}

        /**
         * Filters SkyBlock items by their {@link SkyBlockCategory}.
         * Example: {@code skyblock:SWORD}
         */
        record SkyBlockCategory(String category) implements ItemFilter {}

        /**
         * Filters SkyBlock items to those tradeable on the Bazaar —
         * Annotation value: {@code "skyblock:bazaar"}
         */
        record SkyBlockBazaar() implements ItemFilter {}

        /**
         * Parses a raw {@code @ItemTag} annotation value into a typed filter.
         * Never returns {@code null}.
         */
        static ItemFilter parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return new All();

            String normalized = raw.contains(":") ? raw : "minecraft:" + raw;
            Identifier id = Identifier.tryParse(normalized);

            if (id == null) {
                LOG.warn("ItemFilter: unparseable tag value '%s', falling back to All".formatted(raw));

                return new All();
            }

            return switch (id.getNamespace()) {
                case "skyblock" -> switch (id.getPath()) {
                    case "bazaar" -> new SkyBlockBazaar();
                    default -> new SkyBlockCategory(id.getPath().toUpperCase(Locale.ROOT));
                };
                default -> new VanillaTag(id);
            };
        }
    }

    public static void buildSkyBlockItemsCache() {
        if (SKYBLOCK_REPO_READY) return;
        else SKYBLOCK_REPO_READY = true;

        SKYBLOCK_ITEMS_CACHE = buildSkyBlockItems();
        LOG.info("SkyBlock items cache built — {} items", SKYBLOCK_ITEMS_CACHE.size());
    }

    /**
     * Resolves a persisted ID string back to an {@link ItemStack}.
     * Tries SkyBlock first (if ready), then falls back to the vanilla registry.
     */
    public static @Nullable ItemStack resolve(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;

        return RESOLVED_CACHE.computeIfAbsent(rawId, id -> {
            if (SKYBLOCK_REPO_READY) {
                ItemStack result = SkyBlockItemsRepo.INSTANCE.getItemStack(id);
                if (result != null) return result;
            }

            return resolveVanilla(id);
        });
    }

    /**
     * Returns the canonical string ID to persist for a chosen {@link ItemStack}.
     * SkyBlock stacks use their Hypixel item ID ({@code "HYPERION"});
     * vanilla stacks use their registry key ({@code "minecraft:diamond"}).
     */
    public static String identify(ItemStack stack) {
        if (SKYBLOCK_REPO_READY) {
            String sbId = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getID());

            if (sbId != null) return sbId;
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Returns all items matching a raw {@code @ItemTag} annotation value. */
    public static List<ItemStack> getItems(@Nullable String filterRaw) {
        return getItems(ItemFilter.parse(filterRaw));
    }

    /** Returns all items (no filter). */
    public static List<ItemStack> getItems() {
        return getItems(new ItemFilter.All());
    }

    /** Returns all items matching a pre-parsed {@link ItemFilter}. */
    public static List<ItemStack> getItems(ItemFilter filter) {
        return switch (filter) {
            case ItemFilter.All ignored -> {
                List<ItemStack> vanilla = getVanillaItems(null);
                if (!SKYBLOCK_REPO_READY) yield vanilla;
                yield Stream.concat(vanilla.stream(), skyBlockItems().stream()).toList();
            }

            case ItemFilter.VanillaTag(Identifier tagId) -> getVanillaItems(tagId);

            case ItemFilter.SkyBlockCategory(String category) -> {
                if (!SKYBLOCK_REPO_READY) yield List.of();

                yield getSkyBlockByCategory(category);
            }

            case ItemFilter.SkyBlockBazaar ignored -> {
                if (!SKYBLOCK_REPO_READY) yield List.of();

                yield getSkyBlockBazaarItems();
            }
        };
    }

    private static @Nullable ItemStack resolveVanilla(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;

        return BuiltInRegistries.ITEM.getOptional(identifier)
                .map(ItemStack::new)
                .orElse(null);
    }

    private static List<ItemStack> getVanillaItems(@Nullable Identifier tagId) {
        Stream<Item> items = BuiltInRegistries.ITEM.stream();

        if (tagId != null) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            items = items.filter(item -> BuiltInRegistries.ITEM.wrapAsHolder(item).is(tagKey));
        }

        return items.map(ItemStack::new).toList();
    }

    /** Returns the live SkyBlock items list, building it on first call if needed. */
    private static List<ItemStack> skyBlockItems() {
        List<ItemStack> cached = SKYBLOCK_ITEMS_CACHE;

        return cached.isEmpty() ? buildSkyBlockItems() : cached;
    }

    private static List<ItemStack> getSkyBlockByCategory(String category) {
        SkyBlockCategory target = SkyBlockCategory.Companion.create(category);

        return skyBlockItems().stream()
                .filter(stack -> {
                    SkyBlockCategory stackCategory = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCATEGORY());

                    return stackCategory != null && target.equals(stackCategory, false);
                })
                .toList();
    }

    /**
     * Filters SkyBlock items to those tradeable on the Bazaar.
     */
    private static List<ItemStack> getSkyBlockBazaarItems() {
        BazaarConversions.ensureLoaded();
        Map<String, String> conversions = BazaarConversions.getNameToProductIdCache();

        if (conversions.isEmpty()) {
            LOG.warn("ItemsRepo/bazaar: conversions cache empty — check BazaarConversions/updater init");

            return List.of();
        }

        return skyBlockItems().stream()
                .filter(stack -> conversions.containsKey(Util.stripFormatCodes(stack.getHoverName().getString()).toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static List<ItemStack> buildSkyBlockItems() {
        return RepoAPI.items().items().keySet().stream()
                .map(SkyBlockItemsRepo.INSTANCE::getItemStack)
                .filter(Objects::nonNull)
                .toList();
    }
}