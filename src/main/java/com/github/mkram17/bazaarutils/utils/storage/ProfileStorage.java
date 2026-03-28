package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<ProfileStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @Module
    public static final class Listener extends BUListener {
        @Subscription
        public void onProfileSwitch(ProfileChangeEvent event) {
            currentProfile = event.getName();
        }

        @Subscription
        @TimePassed(duration = "5s")
        public void onTick(TickEvent event) {
            if (REQUIRES_SAVE.isEmpty()) return;
            ProfileStorage<?>[] toSave = REQUIRES_SAVE.toArray(new ProfileStorage<?>[0]);
            REQUIRES_SAVE.clear();
            CompletableFuture.runAsync(() -> {
                for (ProfileStorage<?> s : toSave) s.saveToSystem();
            });
        }
    }

    @Nullable
    private static String currentProfile = null;

    @Subscription()
    public void onProfileSwitch(ProfileChangeEvent event) {
        currentProfile = event.getName();
    }

    @Subscription()
    @TimePassed(duration = "5s")
    public void onTick(TickEvent event) {
        if (REQUIRES_SAVE.isEmpty()) return;
        ProfileStorage<?>[] toSave = REQUIRES_SAVE.toArray(new ProfileStorage<?>[0]);
        REQUIRES_SAVE.clear();
        CompletableFuture.runAsync(() -> {
            for (ProfileStorage<?> s : toSave) s.saveToSystem();
        });
    }

    private final int version;
    private final Supplier<T> defaultData;
    private final String fileName;
    private final Function<Integer, Codec<T>> codec;

    @Nullable private T data;
    @Nullable private Path lastPath;
    @Nullable private String lastProfile;

    public ProfileStorage(int version, Supplier<T> defaultData, String fileName, Function<Integer, Codec<T>> codec) {
        this.version = version;
        this.defaultData = defaultData;
        this.fileName = fileName;
        this.codec = codec;
    }

    public ProfileStorage(Supplier<T> defaultData, String fileName, Codec<T> codec) {
        this(0, defaultData, fileName, v -> codec);
    }

    private boolean isCurrentlyActive() {
        return lastProfile != null && currentProfile != null && currentProfile.equals(lastProfile);
    }

    @Nullable
    public T get() {
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
        T d = get();
        if (d == null) return;
        modifier.accept(d);
        save();
    }

    public void save() { REQUIRES_SAVE.add(this); }

    public void delete() {
        if (lastPath == null) return;
        try { Files.deleteIfExists(lastPath); }
        catch (IOException e) { Util.logError("Failed to delete " + lastPath, e); }
    }

    private void load() {
        if (currentProfile == null) return;

        lastProfile = currentProfile;
        lastPath = DataStorage.DEFAULT_PATH
                .resolve(McPlayer.INSTANCE.getUuid().toString())
                .resolve(lastProfile)
                .resolve(fileName + ".json");

        if (!Files.exists(lastPath)) {
            try { Files.createDirectories(lastPath.getParent()); }
            catch (IOException e) { Util.logError("Failed to create profile data directory", e); }
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

            for (int v = fileVersion; v < this.version; v++) {
                T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
                dataEl = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
            }

            data = codec.apply(this.version).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
        } catch (Exception e) {
            Util.logError("Failed to load profile data from " + lastPath + ", using defaults.", e);
            data = defaultData.get();
            saveToSystem();
        }
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
        } catch (Exception e) {
            Util.logError("Failed to save profile data " + data + " to file", e);
        }
    }
}