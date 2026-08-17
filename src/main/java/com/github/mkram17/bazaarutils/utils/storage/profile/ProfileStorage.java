package com.github.mkram17.bazaarutils.utils.storage.profile;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.github.mkram17.bazaarutils.utils.storage.JsonStorageIO;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;
import tech.thatgravyboat.skyblockapi.helpers.McPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProfileStorage<T> {
    private static final Set<ProfileStorage<?>> REQUIRES_SAVE = ConcurrentHashMap.newKeySet();

    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription(priority = Priority.FIRST)
        public void onProfileSwitch(ProfileChangeEvent event) {
            Util.logMessage("Profile switch → %s".formatted(event.getName()));
            currentProfile = event.getName();
        }

        @Subscription(priority = Priority.LAST)
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

        for (ProfileStorage<?> storage : List.copyOf(REQUIRES_SAVE)) {
            if (!REQUIRES_SAVE.remove(storage)) continue;
            if (!storage.saveToSystem()) {
                REQUIRES_SAVE.add(storage);
            }
        }
    }

    @Nullable
    private static volatile String currentProfile = null;

    /**
     * Immutable pairing of the loaded data with the player UUID, profile name, and file it came from.
     * Kept in a single field so the four can only ever be swapped together — nothing can observe one
     * player/profile's data alongside another's path.
     */
    private record Slot<T>(T data, Path path, UUID playerUuid, String profile) {}

    private final int version;
    private final Supplier<@NotNull T> defaultData;
    private final String fileName;
    private final Function<Integer, Codec<T>> codec;

    @Nullable private volatile Slot<T> slot;
    /** Guards against re-entrant {@link #load()} calls made by codecs that read this storage while decoding. */
    private volatile boolean loading = false;
    /** Receives freshly loaded data once it is the active slot. */
    @Nullable private final Consumer<T> onLoad;
    /** Receives data that is about to stop being the active slot, while it is still reachable. */
    @Nullable private final Consumer<T> onUnload;

    public ProfileStorage(int version, Supplier<@NotNull T> defaultData, String fileName, Function<Integer, Codec<T>> codec, @Nullable Consumer<T> onLoad, @Nullable Consumer<T> onUnload) {
        this.version = version;
        this.defaultData = defaultData;
        this.fileName = fileName;
        this.codec = codec;
        this.onLoad = onLoad;
        this.onUnload = onUnload;
    }

    public ProfileStorage(int version, Supplier<T> defaultData, String fileName, Function<Integer, Codec<T>> codec) {
        this(version, defaultData, fileName, codec, null, null);
    }

    public ProfileStorage(Supplier<T> defaultData, String fileName, Codec<T> codec) {
        this(0, defaultData, fileName, v -> codec, null, null);
    }

    private boolean isCurrentlyActive() {
        Slot<T> current = slot;
        UUID activeUuid = McPlayer.INSTANCE.getUuid();

        return current != null
                && Objects.equals(current.profile(), currentProfile)
                && Objects.equals(current.playerUuid(), activeUuid);
    }

    public @Nullable T get() {
        if (isCurrentlyActive() || loading) {
            Slot<T> current = slot;

            return current == null ? null : current.data();
        }

        if (REQUIRES_SAVE.remove(this) && !saveToSystem()) {
            REQUIRES_SAVE.add(this);
        }
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

        replaceSlot(new Slot<>(newData, current.path(), current.playerUuid(), current.profile()));
        save();
    }

    /**
     * The only place {@link #slot} is assigned, so every swap tears down the outgoing data and sets up the
     * incoming data. {@link #onUnload} runs while the outgoing data is still reachable; {@link #onLoad}
     * runs once the incoming data is the active slot, so hooks that read this storage see the new value.
     */
    private void replaceSlot(@Nullable Slot<T> next) {
        Slot<T> outgoing = slot;
        if (outgoing != null && onUnload != null) onUnload.accept(outgoing.data());

        slot = next;

        if (next != null && onLoad != null) onLoad.accept(next.data());
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
        REQUIRES_SAVE.remove(this);
        Slot<T> current = slot;
        if (current == null) return;

        try {
            Files.deleteIfExists(current.path());
            Util.logMessage("Deleted %s".formatted(DataStorage.DEFAULT_PATH.relativize(current.path())));
        } catch (IOException exception) {
            Util.logError("Failed to delete %s".formatted(DataStorage.DEFAULT_PATH.relativize(current.path())), exception);
        } finally {
            replaceSlot(null);
        }
    }

    private void load() {
        String profile = currentProfile;
        UUID playerUuid = McPlayer.INSTANCE.getUuid();
        if (playerUuid == null || profile == null || profile.isBlank() || loading) return;

        loading = true;

        try {
            Path path = DataStorage.DEFAULT_PATH
                    .resolve(playerUuid.toString())
                    .resolve(profile)
                    .resolve(fileName + ".json");

            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Util.logMessage("No existing data for profile=%s file=%s — initialising defaults".formatted(profile, fileName));

                replaceSlot(new Slot<>(defaultData.get(), path, playerUuid, profile));
                saveToSystem();
            } else {
                try {
                    // readAndMigrate runs before replaceSlot so a decode failure leaves the outgoing data in place.
                    var result = JsonStorageIO.readAndMigrate(path, this.version, codec);

                    replaceSlot(new Slot<>(result.value(), path, playerUuid, profile));
                    if (result.migrated()) save();
                    Util.logMessage("Loaded %s for profile=%s (v%d)".formatted(fileName, profile, this.version));
                } catch (Exception exception) {
                    Util.logError("Failed to load saved data for profile %s (%s)".formatted(profile, fileName), exception);

                    // Never silently overwrite data we could not read — keep it around so it can be recovered.
                    JsonStorageIO.quarantine(path);

                    replaceSlot(new Slot<>(defaultData.get(), path, playerUuid, profile));
                    saveToSystem();
                }
            }
        } catch (Exception exception) {
            // Leaves the slot untouched so the next get() retries instead of latching onto stale/absent data.
            Util.logError("Failed to resolve profile data for profile=%s file=%s".formatted(profile, fileName), exception);
        } finally {
            loading = false;
        }
    }

    private boolean saveToSystem() {
        Slot<T> current = slot;
        if (current == null) return true;

        try {
            JsonStorageIO.writeAtomic(current.path(), version, codec.apply(version), current.data());
            Util.logMessage("Saved %s for profile=%s".formatted(fileName, current.profile()));

            return true;
        } catch (Exception exception) {
            Util.logError("Failed to write %s for profile %s — your progress may not be saved".formatted(fileName, current.profile()), exception);

            return false;
        }
    }
}
