package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.utils.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

public interface ProductInfo {
    @NotNull String getProductId();

    static boolean isValidProductId(@Nullable String productId) {
        if (productId == null || productId.isEmpty()) return false;

        ResourceManager.ensureConversionsLoaded();
        return ResourceManager.getProductIdtoNameCache().containsKey(productId);
    }

    static boolean isValidDisplayName(@Nullable String name) {
        if (name == null || name.isBlank()) return false;

        ResourceManager.ensureConversionsLoaded();
        return ResourceManager.getNameToProductIdCache().containsKey(name.toLowerCase(Locale.ROOT));
    }

    static Optional<ProductInfo> fromDisplayName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        ResourceManager.ensureConversionsLoaded();
        return Optional.ofNullable(ResourceManager.getNameToProductIdCache().get(name.toLowerCase(Locale.ROOT))).map(id -> () -> id);
    }

    default boolean isValidProductId() {
        return isValidProductId(getProductId());
    }
}
