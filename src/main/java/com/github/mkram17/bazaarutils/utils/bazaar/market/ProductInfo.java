package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.resources.BazaarConversions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Identity interface for any object associated with a Bazaar product.
 *
 * <p>Implemented by {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData},
 * {@link com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo},
 * {@link com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo}, and the
 * package-private {@code SimpleProduct} record.
 *
 * <p>Static lookup methods delegate to {@code BazaarConversions}, which maintains bidirectional
 * name↔ID caches loaded from packaged resource data. Name lookups are case-insensitive. Unknown
 * names or IDs return {@link java.util.Optional#empty()}; callers must guard.
 */
public interface ProductInfo {
    @NotNull String getProductId();

    @NotNull
    default String getName() {
        BazaarConversions.ensureLoaded();

        return Optional.ofNullable(BazaarConversions.getProductIdToNameCache().get(getProductId())).orElse(getProductId()); //
    }

    static boolean isValidProductId(@Nullable String productId) {
        if (productId == null) return false;

        BazaarConversions.ensureLoaded();
        return BazaarConversions.getProductIdToNameCache().containsKey(productId);
    }

    static boolean isValidDisplayName(@Nullable String name) {
        if (name == null) return false;

        BazaarConversions.ensureLoaded();
        return BazaarConversions.getNameToProductIdCache().containsKey(name.toLowerCase(Locale.ROOT));
    }

    static Optional<ProductInfo> fromDisplayName(@Nullable String name) {
        if (name == null) return Optional.empty();

        BazaarConversions.ensureLoaded();
        String id = BazaarConversions.getNameToProductIdCache().get(name.toLowerCase(Locale.ROOT));

        return id == null ? Optional.empty() : Optional.of(new SimpleProduct(id, name));
    }

    static Optional<ProductInfo> fromProductId(@Nullable String productId) {
        if (productId == null) return Optional.empty();

        BazaarConversions.ensureLoaded();
        String name = BazaarConversions.getProductIdToNameCache().get(productId);

        return name == null ? Optional.empty() : Optional.of(new SimpleProduct(productId, name));
    }

    /** Resolves an {@link ItemStack} to its Bazaar product ID. */
    static Optional<ProductInfo> fromItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        BazaarConversions.ensureLoaded();

        String cleanName = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());
        if (cleanName == null) return Optional.empty();

        if ("Enchanted Book".equals(cleanName)) {
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) return Optional.empty();

            Map<String, String> nameToId = BazaarConversions.getNameToProductIdCache();

            for (var line : lore.lines()) {
                String loreLine = Util.removeFormatting(line.getString());
                String id = nameToId.get(loreLine.toLowerCase(Locale.ROOT));

                if (id != null) {
                    return Optional.of(new SimpleProduct(id, loreLine));
                }
            }

            return Optional.empty();
        }

        return fromDisplayName(cleanName);
    }

    default boolean isValidProductId() {
        return isValidProductId(getProductId());
    }
}

// Silly private record such that #fromDisplayName further #getName calls don't hit the cache for name queries
record SimpleProduct(String productId, String name) implements ProductInfo {
    public @NotNull String getProductId() {
        return productId;
    }

    public @NotNull String getName() {
        return name;
    }
}