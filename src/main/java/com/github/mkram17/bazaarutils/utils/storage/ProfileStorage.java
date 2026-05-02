package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;
import tech.thatgravyboat.skyblockapi.helpers.McPlayer;

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

public class ProfileStorage<T> {
    private static final BazaarLogger LOG = BazaarLogger.of(ProfileStorage.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<ProfileStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription(priority = Integer.MIN_VALUE)
        public void onProfileSwitch(ProfileChangeEvent event) {
            LOG.info("Profile switch → {}", event.getName());
            currentProfile = event.getName();
        }

        @Subscription(priority = Integer.MAX_VALUE)
        @TimePassed(duration = "5s")
        public void onTick(TickEvent event) {
            if (REQUIRES_SAVE.isEmpty()) return;
            ProfileStorage<?>[] toSave = REQUIRES_SAVE.toArray(new ProfileStorage<?>[0]);
            REQUIRES_SAVE.clear();
            CompletableFuture.runAsync(() -> {
                for (ProfileStorage<?> storage : toSave) storage.saveToSystem();
            });
        }
    }

    @Nullable
    private static String currentProfile = null;

    private final int version;
    private final Supplier<@NotNull T> defaultData;
    private final String fileName;
    private final Function<Integer, Codec<T>> codec;

    @Nullable private volatile T data;
    @Nullable private Path lastPath;
    @Nullable private String lastProfile;
    @Nullable private final Consumer<ProfileStorage<T>> onProfileSwitch;

    public ProfileStorage(int version, Supplier<@NotNull T> defaultData, String fileName, Function<Integer, Codec<T>> codec, @Nullable Consumer<ProfileStorage<T>> onProfileSwitch) {
        this.version = version;
        this.defaultData = defaultData;
        this.fileName = fileName;
        this.codec = codec;
        this.onProfileSwitch = onProfileSwitch;
    }

    public ProfileStorage(int version, Supplier<T> defaultData, String fileName, Function<Integer, Codec<T>> codec) {
        this(version, defaultData, fileName, codec, null);
    }

    public ProfileStorage(Supplier<T> defaultData, String fileName, Codec<T> codec) {
        this(0, defaultData, fileName, v -> codec, null);
    }

    private boolean isCurrentlyActive() {
        return currentProfile != null && currentProfile.equals(lastProfile);
    }

    public @Nullable T get() {
        if (isCurrentlyActive()) return data;

        saveToSystem();
        load();

        return data;
    }

    public void set(T newData) {
        if (isCurrentlyActive()) {
            this.data = newData;
            save();
            return;
        }

        saveToSystem();
        load();

        if (data != null) {
            this.data = newData;
            save();
        }
    }

    public void edit(Consumer<T> modifier) {
        T data = get();
        if (data == null) return;

        modifier.accept(data);
        save();
    }

    public void save() {
        REQUIRES_SAVE.add(this);
    }

    public void delete() {
        if (lastPath == null) return;

        try {
            Files.deleteIfExists(lastPath);
            LOG.info("Deleted {}", DataStorage.DEFAULT_PATH.relativize(lastPath));
        } catch (IOException exception) {
            LOG.error("Failed to delete {}", DataStorage.DEFAULT_PATH.relativize(lastPath), exception);
        }
    }

    private void load() {
        if (currentProfile == null) return;

        lastProfile = currentProfile;
        lastPath = DataStorage.DEFAULT_PATH
                .resolve(McPlayer.INSTANCE.getUuid().toString())
                .resolve(lastProfile)
                .resolve(fileName + ".json");

        if (!Files.exists(lastPath)) {
            try {
                Files.createDirectories(lastPath.getParent());
            } catch (IOException exception) {
                LOG.error("Failed to create profile data directory — path={}", lastPath, exception);
            }
            LOG.info("No existing data for profile={} file={} — initialising defaults", lastProfile, fileName);

            data = defaultData.get();
            saveToSystem();

            return;
        }

        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(lastPath, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            int fileVersion = root.get("@bazaarutils:version").getAsInt();
            JsonElement dataEl = root.get("@bazaarutils:data");

            if (fileVersion < this.version) {
                LOG.info("Migrating {} profile={} v{} → v{}", fileName, lastProfile, fileVersion, this.version);
            }

            for (int v = fileVersion; v < this.version; v++) {
                T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
                dataEl = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
            }

            data = codec.apply(this.version).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
            LOG.info("Loaded {} for profile={} (v{})", fileName, lastProfile, this.version);
        } catch (Exception exception) {
            PlayerLogger.sendError("Failed to load saved data for profile %s (%s) — your data may have been reset".formatted(lastProfile, fileName), exception);

            data = defaultData.get();
            saveToSystem();
        }

        if (onProfileSwitch != null) onProfileSwitch.accept(this);
    }

    private void saveToSystem() {
        if (data == null || lastPath == null) return;

        try {
            Files.createDirectories(lastPath.getParent());
            JsonElement encoded = codec.apply(version).encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            root.add("@bazaarutils:data", encoded);
            Files.writeString(lastPath, GSON.toJson(root), StandardCharsets.UTF_8);
            LOG.debug("Saved {} for profile={}", fileName, lastProfile);
        } catch (Exception exception) {
            PlayerLogger.sendError("Failed to write %s for profile %s — your progress may not be saved".formatted(fileName, lastProfile), exception);
        }
    }
}