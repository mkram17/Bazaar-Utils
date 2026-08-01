package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProfileStorage<T> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<ProfileStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription(priority = Integer.MIN_VALUE)
        public void onProfileSwitch(ProfileChangeEvent event) {
            Util.logMessage("Profile switch → %s".formatted(event.getName()));
            currentProfile = event.getName();
        }

        @Subscription(priority = Integer.MAX_VALUE)
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
        ProfileStorage<?>[] toSave = REQUIRES_SAVE.toArray(new ProfileStorage<?>[0]);
        REQUIRES_SAVE.clear();
        for (ProfileStorage<?> storage : toSave) storage.saveToSystem();
    }

    @Nullable
    private static String currentProfile = null;

    /**
     * Immutable pairing of the loaded data with the profile and file it came from. Kept in a single
     * field so the three can only ever be swapped together — nothing can observe one profile's data
     * alongside another profile's path.
     */
    private record Slot<T>(T data, Path path, String profile) {}

    private final int version;
    private final Supplier<@NotNull T> defaultData;
    private final String fileName;
    private final Function<Integer, Codec<T>> codec;

    @Nullable private volatile Slot<T> slot;
    /** Guards against re-entrant {@link #load()} calls made by codecs that read this storage while decoding. */
    private volatile boolean loading = false;
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
        Slot<T> current = slot;

        return current != null && current.profile().equals(currentProfile);
    }

    public @Nullable T get() {
        if (isCurrentlyActive() || loading) {
            Slot<T> current = slot;

            return current == null ? null : current.data();
        }

        saveToSystem();
        load();

        Slot<T> current = slot;

        return current == null ? null : current.data();
    }

    public void set(T newData) {
        // Makes sure the slot belongs to the profile the player is actually on before replacing its data.
        get();

        Slot<T> current = slot;
        if (current == null) {
            Util.logError("Dropped a write to %s — no profile is loaded yet".formatted(fileName), null);

            return;
        }

        slot = new Slot<>(newData, current.path(), current.profile());
        save();
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
        Slot<T> current = slot;
        if (current == null) return;

        try {
            Files.deleteIfExists(current.path());
            Util.logMessage("Deleted %s".formatted(DataStorage.DEFAULT_PATH.relativize(current.path())));
        } catch (IOException exception) {
            Util.logError("Failed to delete %s".formatted(DataStorage.DEFAULT_PATH.relativize(current.path())), exception);
        }
    }

    private void load() {
        String profile = currentProfile;
        if (profile == null || loading) return;

        loading = true;

        try {
            Path path = DataStorage.DEFAULT_PATH
                    .resolve(McPlayer.INSTANCE.getUuid().toString())
                    .resolve(profile)
                    .resolve(fileName + ".json");

            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Util.logMessage("No existing data for profile=%s file=%s — initialising defaults".formatted(profile, fileName));

                slot = new Slot<>(defaultData.get(), path, profile);
                saveToSystem();
            } else {
                try {
                    slot = new Slot<>(parse(path), path, profile);
                    Util.logMessage("Loaded %s for profile=%s (v%d)".formatted(fileName, profile, this.version));
                } catch (Exception exception) {
                    Util.logError("Failed to load saved data for profile %s (%s)".formatted(profile, fileName), exception);

                    // Never silently overwrite data we could not read — keep it around so it can be recovered.
                    backup(path);

                    slot = new Slot<>(defaultData.get(), path, profile);
                    saveToSystem();
                }
            }
        } catch (Exception exception) {
            // Leaves the slot untouched so the next get() retries instead of latching onto stale/absent data.
            Util.logError("Failed to resolve profile data for profile=%s file=%s".formatted(profile, fileName), exception);

            return;
        } finally {
            loading = false;
        }

        if (onProfileSwitch != null) onProfileSwitch.accept(this);
    }

    private T parse(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)
        ).getAsJsonObject();

        int fileVersion = root.get("@bazaarutils:version").getAsInt();
        JsonElement dataEl = root.get("@bazaarutils:data");

        if (fileVersion < this.version) {
            Util.logMessage("Migrating %s v%d → v%d".formatted(fileName, fileVersion, this.version));
        }

        for (int v = fileVersion; v < this.version; v++) {
            T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
            dataEl = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
        }

        return codec.apply(this.version).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
    }

    private static void backup(Path path) {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(path, backup);
            Util.logMessage("Backed up unreadable data to %s".formatted(backup.getFileName()));
        } catch (IOException exception) {
            Util.logError("Failed to back up unreadable data at %s".formatted(path), exception);
        }
    }

    private void saveToSystem() {
        Slot<T> current = slot;
        if (current == null) return;

        try {
            Files.createDirectories(current.path().getParent());
            JsonElement encoded = codec.apply(version).encodeStart(JsonOps.INSTANCE, current.data()).getOrThrow();
            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            root.add("@bazaarutils:data", encoded);
            Files.writeString(current.path(), GSON.toJson(root), StandardCharsets.UTF_8);
            Util.logMessage("Saved %s for profile=%s".formatted(fileName, current.profile()));
        } catch (Exception exception) {
            Util.logError("Failed to write %s for profile %s — your progress may not be saved".formatted(fileName, current.profile()), exception);
        }
    }
}
