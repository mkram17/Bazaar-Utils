package com.github.mkram17.bazaarutils.data.bazaar;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsDataSources;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.github.mkram17.bazaarutils.utils.bazaar.data.ProductData;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Central mutable store for all bazaar market data.
 *
 * <p>Owns the product registry map and all shared mutation helpers used by
 * {@link DataSources} implementations.</p>
 */
@LateInitModule
public final class BazaarDataRegistry {
    public static List<BUListener> SOURCES;

    private static final ConcurrentHashMap<String, ProductData> REGISTRY = new ConcurrentHashMap<>();

    public BazaarDataRegistry() {
        SOURCES = Stream.of(
                        BazaarUtilsPreInitModules.collected,
                        BazaarUtilsModules.collected,
                        BazaarUtilsDataSources.collected
                ).flatMap(List::stream)
                .filter(it -> it instanceof BUListener)
                .map(it -> (BUListener) it)
                .toList();
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