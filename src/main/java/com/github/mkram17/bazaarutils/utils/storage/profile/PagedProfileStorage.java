package com.github.mkram17.bazaarutils.utils.storage.profile;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.github.mkram17.bazaarutils.utils.storage.RetentionPolicy;
import com.github.mkram17.bazaarutils.utils.storage.StoragePolicy;
import com.google.common.cache.*;
import com.google.common.util.concurrent.Striped;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic per-(player, profile) storage engine backed by a Guava {@link LoadingCache} and
 * one JSON file per identity on disk.
 *
 * <p>Reads are cache-first, loading from disk on first touch per identity. Writes are
 * deferred: {@link #set}/{@link #edit} mark the identity dirty and leave the actual disk
 * write to {@link #flushNow}, called every 5 seconds by {@link Listener} across every live
 * instance, or immediately if a dirty entry is evicted from the cache before that — see the
 * removal listener in the constructor.
 *
 * <p>Every flush — periodic or eviction-triggered — runs SYNCHRONOUSLY, on whatever thread
 * calls it. Load-bearing, not incidental: {@link #edit} hands the live cached object to its
 * caller for IN-PLACE mutation, matching {@code DataStorage}/{@code ProfileStorage}'s own
 * {@code edit} contract exactly. Encoding that same object on a background thread while a
 * caller might still be mutating it would be a genuine race — those two classes avoid it by
 * flushing synchronously on the same (game) thread every {@code edit} caller runs on, so the
 * two can never interleave; this class relies on the identical guarantee. There is no
 * wholesale-replacement-only write API here for an async flush to be safe under — {@code set}
 * is the closest, but it's still ordinary dirty-tracked state subject to the same flush.
 *
 * <p>Each file records its own schema version and is migrated forward one step at a time on
 * load — {@code codec} is asked for the {@link Codec} at each intermediate version in turn,
 * so a type only ever needs to know how to read its immediate predecessor, not every version
 * that has ever existed.
 *
 * <p>A corrupted file is handled per {@link StoragePolicy.CorruptionPolicy}: quarantined
 * (renamed aside, replaced with a fresh default) or, under {@code FAIL_FAST}, thrown as a
 * hard failure rather than silently discarded.
 */
public final class PagedProfileStorage<T> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<PagedProfileStorage<?>> INSTANCES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Fires {@link #flushAll} on a 5-second tick for every live {@link PagedProfileStorage} instance. */
    @PreInitModule
    public static final class Listener extends BUListener {
        @Subscription
        @TimePassed(duration = "5s")
        public void onTick(TickEvent event) {
            flushAll();
        }
    }

    /** Flushes every live instance's dirty entries. Synchronous — see the class doc for why. */
    public static void flushAll() {
        for (var instance : List.copyOf(INSTANCES)) instance.flushNow();
    }

    private final int version;
    private final String fileName;
    private final Supplier<@NotNull T> defaultValue;
    private final Function<Integer, Codec<T>> codec;
    private final StoragePolicy policy;

    private final LoadingCache<ProfileIdentity, T> cache;
    private final Set<ProfileIdentity> dirty = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Striped<Lock> locks = Striped.lock(64);

    /**
     * @param version      current schema version; {@code codec} must be able to decode
     *                     every version from the oldest file still on disk up through this one
     * @param fileName     the JSON file name (without extension) written under each
     *                     identity's own directory
     * @param defaultValue supplies a fresh value for an identity with no persisted file, or
     *                     one that failed to load under quarantine policy. If {@code T} is a
     *                     mutable collection, this MUST supply a genuinely mutable instance
     *                     (e.g. {@code ArrayList::new}, never {@code List::of}) — {@link #edit}
     *                     mutates the cached value in place, including one that came straight
     *                     from this supplier with no prior file on disk, and an immutable
     *                     default would throw the first time {@code edit} is ever called on a
     *                     never-before-seen identity.
     * @param codec        the {@link Codec} for a given schema version, used both to read
     *                     an existing file at its recorded version and to write at {@code version}
     * @param policy       retention, load timing, and corruption handling for this instance
     */
    public PagedProfileStorage(int version, @NotNull String fileName, @NotNull Supplier<@NotNull T> defaultValue,
                               @NotNull Function<Integer, Codec<T>> codec, @NotNull StoragePolicy policy) {
        this.version = version;
        this.fileName = fileName;
        this.defaultValue = defaultValue;
        this.codec = codec;
        this.policy = policy;

        RemovalListener<ProfileIdentity, T> removalListener = notification -> {
            // A REPLACED cause fires on every ordinary update() too — the old value is
            // being swapped for a newer one already in the cache, not lost. Only SIZE
            // (capacity eviction) or EXPLICIT genuinely removes a value with nothing
            // to replace it.
            var cause = notification.getCause();
            if (cause != RemovalCause.SIZE && cause != RemovalCause.EXPLICIT) return;

            var identity = notification.getKey();
            var value = notification.getValue();
            if (identity == null || value == null) return;

            if (dirty.remove(identity)) {
                // Still unsaved when evicted — flush it now, synchronously, before it's
                // gone for good. Same thread as whatever triggered the eviction (a
                // cache.put/getUnchecked call inside set()/edit()) — never dispatched
                // elsewhere; see the class doc for why that matters.
                saveToSystem(identity, value);
            }
        };

        CacheBuilder<ProfileIdentity, T> builder = CacheBuilder.newBuilder().removalListener(removalListener);
        if (policy.retention() instanceof RetentionPolicy.Bounded(long maxEntries)) {
            builder.maximumSize(maxEntries);
        }

        this.cache = builder.build(new CacheLoader<>() {
            @Override
            public @NonNull T load(@NotNull ProfileIdentity identity) {
                return loadFromDisk(identity);
            }
        });

        INSTANCES.add(this);

        if (policy.load() == StoragePolicy.LoadPolicy.EAGER) {
            // Unaffected by the synchronous-flush requirement above: this only ever
            // populates cache slots nothing has touched yet, at construction time,
            // before any caller could plausibly be mid-edit() on those identities.
            CompletableFuture.runAsync(() -> knownKeys().forEach(cache::getUnchecked));
        }
    }

    /** @see #PagedProfileStorage(int, String, Supplier, Function, StoragePolicy) — version 0, {@link StoragePolicy#DEFAULT}. */
    public PagedProfileStorage(@NotNull String fileName, @NotNull Supplier<@NotNull T> defaultValue, @NotNull Codec<T> codec) {
        this(0, fileName, defaultValue, v -> codec, StoragePolicy.DEFAULT);
    }

    /** Returns this identity's value, loading it from disk on first touch if not already cached. */
    public @NotNull T get(@NotNull ProfileIdentity identity) {
        return cache.getUnchecked(identity);
    }

    /**
     * Returns every identity this file has ever been persisted for, each loaded to its
     * current value. Forces every disk-known identity into the cache if not already
     * resident — potentially expensive the first time it's called on a large known set.
     */
    public @NotNull Map<ProfileIdentity, T> allKnown() {
        Map<ProfileIdentity, T> result = new LinkedHashMap<>();

        for (var identity : knownKeys()) result.put(identity, get(identity));

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns every identity with a persisted {@code fileName}.json on disk, discovered by
     * walking the storage directory directly — independent of what's currently cache-resident.
     */
    public @NotNull Set<ProfileIdentity> knownKeys() {
        if (!Files.isDirectory(DataStorage.DEFAULT_PATH)) return Set.of();

        try (Stream<Path> playerDirs = Files.list(DataStorage.DEFAULT_PATH)) {
            return playerDirs.filter(Files::isDirectory)
                    .flatMap(this::listProfilesFor)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            Util.logError("Failed to list known identities for %s".formatted(fileName), exception);

            return Set.of();
        }
    }

    /** Replaces this identity's value outright, unconditionally marking it dirty — matches {@code DataStorage.set}'s own unconditional behavior. */
    public void set(@NotNull ProfileIdentity identity, @NotNull T value) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            cache.put(identity, value);
            dirty.add(identity);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads (if absent) this identity's value and hands it to {@code modifier} for IN-PLACE
     * mutation, then marks it dirty. Matches {@code DataStorage}/{@code ProfileStorage}'s own
     * {@code edit(Consumer<T>)} contract exactly — {@code modifier} receives the actual live
     * cached object, not a copy, and is expected to mutate it directly.
     *
     * <p>Always marks dirty, even if {@code modifier} changes nothing — there's no way to
     * detect that when the contract is in-place mutation of a shared reference, same as
     * {@code ProfileStorage.edit}.
     */
    public void edit(@NotNull ProfileIdentity identity, @NotNull Consumer<T> modifier) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            T current = cache.getUnchecked(identity);
            modifier.accept(current);
            dirty.add(identity);
        } finally {
            lock.unlock();
        }
    }

    /** The identities under one player directory that have a persisted {@code fileName}.json. */
    private Stream<ProfileIdentity> listProfilesFor(Path playerDir) {
        UUID playerUuid;

        try {
            playerUuid = UUID.fromString(playerDir.getFileName().toString());
        } catch (IllegalArgumentException notAUuid) {
            return Stream.empty();
        }

        try (Stream<Path> profileDirs = Files.list(playerDir)) {
            return profileDirs.filter(Files::isDirectory)
                    .filter(profileDir -> Files.exists(profileDir.resolve(fileName + ".json")))
                    .map(profileDir -> new ProfileIdentity(playerUuid, profileDir.getFileName().toString()))
                    .toList().stream();
        } catch (IOException exception) {
            return Stream.empty();
        }
    }

    private Path pathFor(ProfileIdentity identity) {
        return DataStorage.DEFAULT_PATH
                .resolve(identity.playerUuid().toString())
                .resolve(identity.profileName())
                .resolve(fileName + ".json");
    }

    /**
     * Reads and decodes the file for {@code identity}, or returns a fresh default when no
     * file exists. A read or decode failure is handled per
     * {@link StoragePolicy.CorruptionPolicy}: quarantined and replaced with a fresh
     * default, or thrown as a hard failure under {@code FAIL_FAST}.
     */
    private T loadFromDisk(ProfileIdentity identity) {
        Path path = pathFor(identity);
        if (!Files.exists(path)) return defaultValue.get();

        try {
            return parse(path);
        } catch (Exception exception) {
            Util.logError("Failed to load %s for %s".formatted(fileName, identity), exception);

            if (policy.corruption() == StoragePolicy.CorruptionPolicy.FAIL_FAST) {
                throw new IllegalStateException("Refusing to silently discard unreadable data at " + path, exception);
            }

            quarantine(path);

            return defaultValue.get();
        }
    }

    /**
     * Decodes the file's JSON, migrating forward one schema version at a time from
     * whatever version the file itself recorded up to {@link #version}.
     */
    private T parse(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();

        int fileVersion = root.get("@bazaarutils:version").getAsInt();

        JsonElement dataEl = root.get("@bazaarutils:data");

        for (int v = fileVersion; v < version; v++) {
            T intermediate = codec.apply(v).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
            dataEl = codec.apply(v + 1).encodeStart(JsonOps.INSTANCE, intermediate).getOrThrow();
        }

        return codec.apply(version).parse(JsonOps.INSTANCE, dataEl).getOrThrow();
    }

    /** Renames a corrupted file aside with a timestamped suffix, atomically where the filesystem supports it. */
    private static void quarantine(Path path) {
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

    /** Writes every currently-dirty identity for this instance to disk, synchronously, and clears their dirty flag. */
    public void flushNow() {
        for (var identity : Set.copyOf(dirty)) {
            T value = cache.getIfPresent(identity);
            if (value == null) continue;

            saveToSystem(identity, value);
            dirty.remove(identity);
        }
    }

    /**
     * Encodes and writes {@code value} for {@code identity}, via a temp file and an atomic
     * rename so a reader never observes a partially-written file.
     */
    private void saveToSystem(ProfileIdentity identity, T value) {
        Path path = pathFor(identity);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            Files.createDirectories(path.getParent());

            JsonElement encoded = codec.apply(version).encodeStart(JsonOps.INSTANCE, value).getOrThrow();

            JsonObject root = new JsonObject();
            root.addProperty("@bazaarutils:version", version);
            root.add("@bazaarutils:data", encoded);

            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);

            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notSupported) {
                // Non-atomic-move filesystems are rare — fall back rather than fail outright.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }

            Util.logMessage("Saved %s for %s".formatted(fileName, identity));
        } catch (Exception exception) {
            Util.logError("Failed to write %s for %s — your progress may not be saved".formatted(fileName, identity), exception);
        }
    }
}