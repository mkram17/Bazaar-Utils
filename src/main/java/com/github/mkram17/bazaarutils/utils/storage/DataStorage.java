package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.codecs.CodecGsonAdapter;
import com.github.mkram17.bazaarutils.utils.codecs.ZonedDateTimeCodec;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataStorage<T> {
    public static final Path DEFAULT_PATH = FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID).resolve("data");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ItemStack.class, new CodecGsonAdapter<>(ItemStack.CODEC))
            .registerTypeAdapter(ZonedDateTime.class, new CodecGsonAdapter<>(ZonedDateTimeCodec.CODEC))
            .create();

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

    public static void flushAll() {
        DataStorage<?>[] toSave = REQUIRES_SAVE.toArray(new DataStorage<?>[0]);
        REQUIRES_SAVE.clear();
        for (DataStorage<?> s : toSave) s.saveToSystem();
    }

    /**
     * Writes this storage now, leaving every other pending storage alone.
     *
     * <p>The alternative for a caller that needs one file on disk immediately is
     * {@link #flushAll()}, which is a worse tool for the job in two ways: it writes every dirty
     * storage on the caller's thread, and its {@code toArray}/{@code clear} pair is not atomic, so
     * a storage marked dirty between the two is dropped from the set without being written. Doing
     * that from an HTTP callback — which is where the one caller lives — means racing the tick
     * listener for unrelated files, to save one.</p>
     */
    public void flush() {
        REQUIRES_SAVE.remove(this);
        saveToSystem();
    }

    private final Function<Integer, Type> codec;
    private final Type currentCodec;
    private final int version;
    private final Path path;
    private T data;


    public DataStorage(int version, Supplier<T> defaultData, String fileName, Function<Integer, Type> codec) {
        this.version = version;
        this.codec = codec;
        this.path = DEFAULT_PATH.resolve(fileName + ".json");
        this.data = load(defaultData);
        this.currentCodec = codec.apply(version);
    }

    public DataStorage(int version, Supplier<T> defaultData, String fileName, Type dataType) {
        this(version, defaultData, fileName, v -> dataType);
    }

    public DataStorage(Supplier<T> defaultData, String fileName, Type dataType) {
        this(0, defaultData, fileName, v -> dataType);
    }

    public T get() { return data; }
    public void set(T newData) { this.data = newData; }
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
                Object intermediate = GSON.fromJson(data, codec.apply(v));
                data = GSON.toJsonTree(intermediate, codec.apply(v));
            }

            T loaded = GSON.fromJson(data, codec.apply(this.version));

            return loaded != null ? loaded : defaultData.get();
        } catch (Exception e) {
            Util.logError("Failed to load " + DEFAULT_PATH.relativize(path) + ", using defaults.", e);

            return defaultData.get();
        }
    }

    private void saveToSystem() {
        Util.logMessage("Saving " + path);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            JsonElement encoded = GSON.toJsonTree(data, currentCodec);
            if (encoded == null) {
                Util.logMessage("Failed to encode " + data + " to json");
                return;
            }
            root.add("@bazaarutils:data", encoded);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            Util.logMessage("Saved " + path);
        } catch (Exception e) {
            Util.logError("Failed to save " + data + " to file", e);
        }
    }
}