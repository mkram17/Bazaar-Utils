package com.github.mkram17.bazaarutils.data.bazaar;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsDataSources;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.github.mkram17.bazaarutils.data.bazaar.book.ProductData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central mutable store for all bazaar market data.
 *
 * <p>Owns the product registry map and all shared mutation helpers used by
 * {@link BazaarDataOrigin} implementations.</p>
 */
@LateInitModule
public final class BazaarDataRegistry {
    public static List<BUListener> SOURCES = new ArrayList<>(List.of());

    private static final ConcurrentHashMap<String, ProductData> REGISTRY = new ConcurrentHashMap<>();

    public BazaarDataRegistry() {
        SOURCES = BazaarUtilsDataSources.collected.stream()
                .map(it -> (BUListener) it)
                .toList();

        Util.logMessage("BazaarDataRegistry initialised — %d sources registered".formatted(SOURCES.size()));
    }

    public static @NotNull ProductData getOrCreate(@NotNull String productId) {
        return REGISTRY.computeIfAbsent(productId, ProductData::new);
    }

    /**
     * Returns the data for {@code productId}, or {@code null} if never seen.
     */
    public static @Nullable ProductData get(@NotNull String productId) {
        return REGISTRY.get(productId);
    }
}