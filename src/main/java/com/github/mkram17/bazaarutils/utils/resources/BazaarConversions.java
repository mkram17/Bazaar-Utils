package com.github.mkram17.bazaarutils.utils.resources;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory lookup cache for Bazaar product conversions.
 */
public final class BazaarConversions {

    private BazaarConversions() {}

    private record ConversionCache(@NotNull Map<String, String> nameToId, @NotNull Map<String, String> idToName) {
        static final ConversionCache EMPTY = new ConversionCache(Map.of(), Map.of());
    }

    private static final AtomicReference<ConversionCache> CACHE = new AtomicReference<>(ConversionCache.EMPTY);

    /** @return lowercase display name → product ID, never null */
    public static Map<String, String> getNameToProductIdCache() {
        return CACHE.get().nameToId();
    }

    /** @return product ID → display name, never null */
    public static Map<String, String> getProductIdToNameCache() {
        return CACHE.get().idToName();
    }

    public static void ensureLoaded() {
        if (isLoaded()) return;
        BazaarConversions.load(BazaarConversionsUpdater.readResourceJson());
    }

    public static boolean isLoaded() {
        return CACHE.get() != ConversionCache.EMPTY;
    }

    /**
     * Parses {@code resourceJson} and atomically replaces the live cache.
     * Safe to call from any thread.
     */
    static void load(JsonObject resourceJson) {
        try {
            Map<String, String> nameToId = new HashMap<>();
            Map<String, String> idToName = new HashMap<>();

            for (String key : resourceJson.keySet()) {
                String value = resourceJson.get(key).getAsString();
                if (value != null) {
                    nameToId.put(value.toLowerCase(Locale.ROOT), key);
                    idToName.put(key, value);
                }
            }

            CACHE.set(new ConversionCache(
                    Collections.unmodifiableMap(nameToId),
                    Collections.unmodifiableMap(idToName)
            ));

            PlayerActionUtil.notifyAll("Resource cache loaded — %d entries".formatted(nameToId.size()), NotificationType.BAZAARDATA);
        } catch (Exception exception) {
            Util.notifyError("Failed to load resource cache — most features will not work. Try /bu updateresources or restart the game.", exception);
            CACHE.set(ConversionCache.EMPTY);
        }
    }

    /** Clears the cache, forcing the next {@link BazaarConversions#ensureLoaded()} call to re-parse. */
    static void invalidate() {
        CACHE.set(ConversionCache.EMPTY);
    }
}