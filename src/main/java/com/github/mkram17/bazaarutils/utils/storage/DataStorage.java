package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
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
    public static final Path DEFAULT_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("bazaarutils")
            .resolve("data");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<DataStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @Module
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

    private final int version;
    private final Function<Integer, Codec<T>> codec;
    private final Codec<T> currentCodec;
    private final Path path;
    private final T data;

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

    public T get() { return data; }

    public void edit(Consumer<T> modifier) {
        modifier.accept(data);
        save();
    }

    public void save() { REQUIRES_SAVE.add(this); }

    public void delete() {
        try { Files.deleteIfExists(path); }
        catch (IOException e) { Util.logError("Failed to delete " + path, e); }
    }

    private T load(Supplier<T> defaultData) {
        if (!Files.exists(path)) {
            try { Files.createDirectories(path.getParent()); }
            catch (IOException e) { Util.logError("Failed to create data directory", e); }
            return defaultData.get();
        }
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            int fileVersion = root.get("@bazaarutils:version").getAsInt();
            JsonElement data = root.get("@bazaarutils:data");

            for (int v = fileVersion; v < this.version; v++) {
                T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, data).getOrThrow();
                data = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
            }

            return codec.apply(this.version).parse(JsonOps.INSTANCE, data).getOrThrow();
        } catch (Exception e) {
            Util.logError("Failed to load " + DEFAULT_PATH.relativize(path) + ", using defaults.", e);

            return defaultData.get();
        }
    }

    private void saveToSystem() {
        try {
            Files.createDirectories(path.getParent());
            JsonElement encoded = currentCodec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            root.add("@bazaarutils:data", encoded);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Util.logError("Failed to save " + data + " to file", e);
        }
    }
}