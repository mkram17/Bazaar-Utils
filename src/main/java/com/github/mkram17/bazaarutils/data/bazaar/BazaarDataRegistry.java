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
 * Central registry for per-product order book data, keyed by Bazaar product ID.
 *
 * <p>Entries are created lazily via {@link #getOrCreate} on first write. Sources that may
 * encounter an unknown product (fills, cancels) use {@link #get} and no-op on {@code null}.
 * Sources that own the write (placements, snapshots) use {@link #getOrCreate}.
 *
 * <p>{@code SOURCES} is populated at module init time from the generated
 * {@code BazaarUtilsDataSources} registry and is the authoritative list of active
 * data-source event listeners.
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

    /** Returns the data for {@code productId}, creating and registering an empty entry if none exists yet. */
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