package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.github.mkram17.bazaarutils.utils.storage.profile.PagedProfileStorage;
import com.github.mkram17.bazaarutils.utils.storage.profile.ProfileStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * Shared low-level JSON envelope I/O, forward schema migration, atomic temp-file
 * persistence, corruption quarantine, and client-shutdown flushing for
 * {@link DataStorage}, {@link ProfileStorage}, and {@link PagedProfileStorage}.
 */
public final class JsonStorageIO {
    private static final String VERSION_KEY = "@bazaarutils:version";
    private static final String DATA_KEY = "@bazaarutils:data";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonStorageIO() {}

    /** Decoded value along with its persisted schema version and whether migration ran. */
    public record ReadResult<T>(@NotNull T value, int fileVersion, boolean migrated) {}

    /** Flushes every dirty storage synchronously on client shutdown so recent writes are never lost. */
    @PreInitModule
    public static final class ShutdownFlushListener extends BUListener {
        @Override
        protected void registerFabricEvents() {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flushAllStorages());
        }

        private static void flushAllStorages() {
            flushSafely("DataStorage", DataStorage::flushAll);
            flushSafely("ProfileStorage", ProfileStorage::flushAll);
            flushSafely("PagedProfileStorage", PagedProfileStorage::flushAll);
        }

        private static void flushSafely(String name, Runnable flusher) {
            try {
                flusher.run();
            } catch (Exception e) {
                Util.logError("Failed to flush " + name + " during client shutdown", e);
            }
        }
    }

    /**
     * Reads a versioned JSON envelope from {@code path}, migrating forward one version
     * step at a time up to {@code targetVersion}.
     *
     * @throws IllegalStateException if the file records a schema version newer than
     *                               {@code targetVersion} or is missing required envelope fields
     */
    public static <T> @NotNull ReadResult<T> readAndMigrate(
            @NotNull Path path,
            int targetVersion,
            @NotNull Function<Integer, Codec<T>> codec) throws IOException {
        JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("Expected JSON object root in " + path);
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonElement versionEl = root.get(VERSION_KEY);
        JsonElement dataEl = root.get(DATA_KEY);
        if (versionEl == null || !versionEl.isJsonPrimitive() || dataEl == null) {
            throw new IllegalStateException("Missing versioned storage envelope fields in " + path);
        }

        int fileVersion = versionEl.getAsInt();
        if (fileVersion > targetVersion) {
            throw new IllegalStateException(
                    "File %s has schema version v%d, newer than supported v%d — refusing to downgrade"
                            .formatted(path.getFileName(), fileVersion, targetVersion));
        }

        boolean migrated = fileVersion < targetVersion;
        if (migrated) {
            Util.logMessage("Migrating %s v%d → v%d".formatted(path.getFileName(), fileVersion, targetVersion));
        }

        for (int v = fileVersion; v < targetVersion; v++) {
            T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
            dataEl = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
        }

        T value = codec.apply(targetVersion).parse(JsonOps.INSTANCE, dataEl).getOrThrow();

        return new ReadResult<>(value, fileVersion, migrated);
    }

    /**
     * Encodes {@code value} at {@code version} into a sibling temp file and atomically
     * moves it into place at {@code path} (falling back to non-atomic replace when the
     * filesystem does not support {@link StandardCopyOption#ATOMIC_MOVE}), cleaning up
     * any partial temp file on failure.
     */
    public static <T> void writeAtomic(
            @NotNull Path path,
            int version,
            @NotNull Codec<T> codec,
            @NotNull T value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();

        JsonObject root = new JsonObject();
        root.addProperty(VERSION_KEY, version);
        root.add(DATA_KEY, encoded);

        Path tmp = path.resolveSibling(path.getFileName() + "." + Thread.currentThread().threadId() + ".tmp");
        try {
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);

            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notSupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException deleteFailure) {
                exception.addSuppressed(deleteFailure);
            }
            throw exception;
        }
    }

    /** Renames an unreadable file aside with a timestamped {@code .corrupt-} suffix. */
    public static void quarantine(@NotNull Path path) {
        if (!Files.exists(path)) return;

        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());

        try {
            try {
                Files.move(path, backup, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notSupported) {
                Files.move(path, backup);
            }

            Util.logMessage("Backed up unreadable data to %s".formatted(backup.getFileName()));
        } catch (IOException exception) {
            Util.logError("Failed to back up unreadable data at %s".formatted(path), exception);
        }
    }
}
