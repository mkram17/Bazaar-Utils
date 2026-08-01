package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataStorage<T> {
    public static final Path DEFAULT_PATH = FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID).resolve("data");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<DataStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription
        @TimePassed(duration = "5s")
        public void onTick(TickEvent event) {
            if (REQUIRES_SAVE.isEmpty()) return;
            DataStorage<?>[] toSave = REQUIRES_SAVE.toArray(new DataStorage<?>[0]);
            REQUIRES_SAVE.clear();
            CompletableFuture.runAsync(() -> {
                for (DataStorage<?> s : toSave) s.saveToSystem();
            });
        }
    }

    public static void flushAll() {
        DataStorage<?>[] toSave = REQUIRES_SAVE.toArray(new DataStorage<?>[0]);
        REQUIRES_SAVE.clear();
        for (DataStorage<?> s : toSave) s.saveToSystem();
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
            JsonObject root = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            int fileVersion = root.get("@bazaarutils:version").getAsInt();
            JsonElement data = root.get("@bazaarutils:data");

            if (fileVersion < this.version) {
                Util.logMessage("Migrating %s v%d → v%d".formatted(path.getFileName(), fileVersion, this.version));
            }

            for (int v = fileVersion; v < this.version; v++) {
                T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, data).getOrThrow();
                data = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
            }

            T result = codec.apply(this.version).parse(JsonOps.INSTANCE, data).getOrThrow();

            Util.logMessage("Loaded %s (v%d)".formatted(DataStorage.DEFAULT_PATH.relativize(path), this.version));

            return result;
        } catch (Exception e) {
            Util.logError("Failed to load " + DEFAULT_PATH.relativize(path) + ", using defaults.", e);

            return defaultData.get();
        }
    }

    private void saveToSystem() {
        Util.logMessage("Saving " + path);
        try {
            Files.createDirectories(path.getParent());
            JsonElement encoded = currentCodec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            root.add("@bazaarutils:data", encoded);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            Util.logMessage("Saved %s (v%d)".formatted(DataStorage.DEFAULT_PATH.relativize(path), version));
        } catch (Exception exception) {
            Util.logError("Failed to save %s — data may be lost".formatted(DataStorage.DEFAULT_PATH.relativize(path)), exception);
        }
    }
}