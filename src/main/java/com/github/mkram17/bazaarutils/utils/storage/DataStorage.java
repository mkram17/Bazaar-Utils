package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.mojang.serialization.Codec;
import net.fabricmc.loader.api.FabricLoader;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataStorage<T> {
    public static final Path DEFAULT_PATH = FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID).resolve("data");

    private static final Set<DataStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription
        @TimePassed(duration = "5s")
        public void onTick(TickEvent event) {
            flushAll();
        }
    }

    /**
     * Runs synchronously on the main thread. Encoding walks the live data, which {@link #edit} and its
     * callers mutate from the main thread — doing it off-thread throws ConcurrentModificationException
     * mid-encode and loses the save.
     */
    public static void flushAll() {
        if (REQUIRES_SAVE.isEmpty()) return;

        for (DataStorage<?> storage : List.copyOf(REQUIRES_SAVE)) {
            if (!REQUIRES_SAVE.remove(storage)) continue;
            if (!storage.saveToSystem()) {
                REQUIRES_SAVE.add(storage);
            }
        }
    }

    private final int version;
    private final Function<Integer, Codec<T>> codec;
    private final Codec<T> currentCodec;
    private final Path path;
    private volatile T data;

    public DataStorage(int version, Supplier<T> defaultData, String fileName, Function<Integer, Codec<T>> codec) {
        this.version = version;
        this.codec = codec;
        this.currentCodec = codec.apply(version);
        this.path = DEFAULT_PATH.resolve(fileName + ".json");
        this.data = load(defaultData);
    }

    public DataStorage(int version, Supplier<T> defaultData, String fileName, Codec<T> codec) {
        this(version, defaultData, fileName, v -> codec);
    }

    public DataStorage(Supplier<T> defaultData, String fileName, Codec<T> codec) {
        this(0, defaultData, fileName, v -> codec);
    }

    public T get() {
        return data;
    }

    public void set(T newData) {
        this.data = newData;
        save();
    }

    public void edit(Consumer<T> modifier) {
        modifier.accept(data);
        save();
    }

    public void save() {
        REQUIRES_SAVE.add(this);
    }

    public void delete() {
        REQUIRES_SAVE.remove(this);
        try {
            Files.deleteIfExists(path);
            Util.logMessage("Deleted %s".formatted(path));
        } catch (IOException e) {
            Util.logError("Failed to delete " + path, e);
        }
    }

    private T load(Supplier<T> defaultData) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                Util.logError("Failed to create data directory", e);
            }
            Util.logMessage("No existing data at %s — initialising defaults".formatted(path));

            return defaultData.get();
        }

        try {
            var result = JsonStorageIO.readAndMigrate(path, this.version, codec);
            if (result.migrated()) save();

            Util.logMessage("Loaded %s (v%d)".formatted(DataStorage.DEFAULT_PATH.relativize(path), this.version));

            return result.value();
        } catch (Exception e) {
            Util.logError("Failed to load " + DEFAULT_PATH.relativize(path) + ", using defaults.", e);
            JsonStorageIO.quarantine(path);

            return defaultData.get();
        }
    }

    private boolean saveToSystem() {
        Util.logMessage("Saving " + path);
        try {
            JsonStorageIO.writeAtomic(path, version, currentCodec, data);
            Util.logMessage("Saved %s (v%d)".formatted(DataStorage.DEFAULT_PATH.relativize(path), version));

            return true;
        } catch (Exception exception) {
            Util.logError("Failed to save %s — data may be lost".formatted(DataStorage.DEFAULT_PATH.relativize(path)), exception);

            return false;
        }
    }
}
