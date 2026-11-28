package com.github.mkram17.bazaarutils.utils.storage.profile;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.github.mkram17.bazaarutils.utils.storage.JsonStorageIO;
import com.github.mkram17.bazaarutils.utils.storage.RetentionPolicy;
import com.github.mkram17.bazaarutils.utils.storage.StoragePolicy;
import com.google.common.cache.*;
import com.google.common.util.concurrent.Striped;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Generic per-(player, profile) storage engine backed by a Guava {@link LoadingCache} and
 * one JSON file per identity on disk.
 *
 * <p>Reads are cache-first, loading from disk on first touch per identity. Writes are
 * deferred: {@link #set}/{@link #edit}/{@link #editIfChanged}/{@link #replaceIfChanged}
 * mark the identity dirty and leave the actual disk write to {@link #flushNow}, called
 * every 5 seconds by {@link Listener} across every live instance, on client shutdown via
 * {@link JsonStorageIO.ShutdownFlushListener}, or immediately if a dirty entry is evicted
 * from the cache before that.
 *
 * <p>Every flush — periodic, shutdown, or eviction-triggered — runs SYNCHRONOUSLY while
 * holding the identity's striped lock so encoding can never race with in-place mutation.
 *
 * <p>Each file records its own schema version and is migrated forward one step at a time on
 * load via {@link JsonStorageIO#readAndMigrate}. Corrupted or future-version files are
 * handled per {@link StoragePolicy.CorruptionPolicy}.
 */
public final class PagedProfileStorage<T> {

    private static final Set<PagedProfileStorage<?>> INSTANCES = ConcurrentHashMap.newKeySet();

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
    private final Set<ProfileIdentity> knownIdentities = ConcurrentHashMap.newKeySet();
    private final Set<ProfileIdentity> dirty = ConcurrentHashMap.newKeySet();
    private final Striped<Lock> locks = Striped.lock(64);

    /**
     * @param version      current schema version; {@code codec} must be able to decode
     *                     every version from the oldest file still on disk up through this one
     * @param fileName     the JSON file name (without extension) written under each
     *                     identity's own directory
     * @param defaultValue supplies a fresh value for an identity with no persisted file, or
     *                     one that failed to load under quarantine policy. If {@code T} is a
     *                     mutable collection, this MUST supply a genuinely mutable instance
     *                     (e.g. {@code ArrayList::new}, never {@code List::of}).
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

            Lock lock = locks.get(identity);
            lock.lock();
            try {
                if (dirty.contains(identity) && saveToSystem(identity, value)) {
                    dirty.remove(identity);
                }
            } finally {
                lock.unlock();
            }
        };

        CacheBuilder<ProfileIdentity, T> builder = CacheBuilder.newBuilder().removalListener(removalListener);
        if (policy.retention() instanceof RetentionPolicy.Bounded(long maxEntries)) {
            builder.maximumSize(maxEntries);
        }

        this.cache = builder.build(new CacheLoader<>() {
            @Override
            public @NotNull T load(@NotNull ProfileIdentity identity) {
                return loadFromDisk(identity);
            }
        });

        this.knownIdentities.addAll(scanDiskKeys());
        INSTANCES.add(this);

        if (policy.load() == StoragePolicy.LoadPolicy.EAGER) {
            CompletableFuture.runAsync(() -> {
                for (var identity : Set.copyOf(knownIdentities)) {
                    try {
                        cache.getUnchecked(identity);
                    } catch (Exception exception) {
                        Util.logError("Failed to eagerly warm %s for %s".formatted(fileName, identity), exception);
                    }
                }
            });
        }
    }

    /** @see #PagedProfileStorage(int, String, Supplier, Function, StoragePolicy) — version 0, {@link StoragePolicy#DEFAULT}. */
    public PagedProfileStorage(@NotNull String fileName, @NotNull Supplier<@NotNull T> defaultValue, @NotNull Codec<T> codec) {
        this(0, fileName, defaultValue, v -> codec, StoragePolicy.DEFAULT);
    }

    /** Returns this identity's value, loading it from disk on first touch if not already cached. */
    public @NotNull T get(@NotNull ProfileIdentity identity) {
        knownIdentities.add(identity);
        return cache.getUnchecked(identity);
    }

    /**
     * Returns every known identity (persisted on disk or touched in memory this session),
     * each loaded to its current value, without performing directory scans on the caller thread.
     */
    public @NotNull Map<ProfileIdentity, T> allKnown() {
        Map<ProfileIdentity, T> result = new LinkedHashMap<>();

        for (var identity : Set.copyOf(knownIdentities)) {
            result.put(identity, cache.getUnchecked(identity));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns every identity known to this storage (discovered on disk at startup plus
     * every identity touched in memory this session), without scanning the filesystem.
     */
    public @NotNull Set<ProfileIdentity> knownKeys() {
        return Set.copyOf(knownIdentities);
    }

    /** Replaces this identity's value outright, unconditionally marking it dirty. */
    public void set(@NotNull ProfileIdentity identity, @NotNull T value) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            knownIdentities.add(identity);
            cache.put(identity, value);
            dirty.add(identity);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads (if absent) this identity's value and hands it to {@code modifier} for IN-PLACE
     * mutation, then unconditionally marks it dirty.
     */
    public void edit(@NotNull ProfileIdentity identity, @NotNull Consumer<T> modifier) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            knownIdentities.add(identity);
            T current = cache.getUnchecked(identity);
            modifier.accept(current);
            dirty.add(identity);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads (if absent) this identity's value and hands it to {@code modifier} for conditional
     * in-place mutation, marking the identity dirty only when {@code modifier} returns {@code true}.
     */
    public boolean editIfChanged(@NotNull ProfileIdentity identity, @NotNull Predicate<T> modifier) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            knownIdentities.add(identity);
            T current = cache.getUnchecked(identity);
            if (!modifier.test(current)) return false;

            dirty.add(identity);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically computes a replacement value for {@code identity} under its per-identity lock,
     * updating the cache and marking dirty only if the returned value differs from the prior one
     * per {@link Objects#equals}. Ideal for immutable {@code record} values.
     */
    public boolean replaceIfChanged(@NotNull ProfileIdentity identity, @NotNull UnaryOperator<T> updater) {
        Lock lock = locks.get(identity);
        lock.lock();
        try {
            knownIdentities.add(identity);
            T current = cache.getUnchecked(identity);
            T next = Objects.requireNonNull(updater.apply(current), "updater returned null");
            if (Objects.equals(current, next)) return false;

            cache.put(identity, next);
            dirty.add(identity);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Walks the storage directory once at startup to discover all persisted identities for {@link #fileName}. */
    private Set<ProfileIdentity> scanDiskKeys() {
        if (!Files.isDirectory(DataStorage.DEFAULT_PATH)) return Set.of();

        try (Stream<Path> playerDirs = Files.list(DataStorage.DEFAULT_PATH)) {
            return playerDirs.filter(Files::isDirectory)
                    .flatMap(this::listProfilesFor)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            Util.logError("Failed to list known identities for %s".formatted(fileName), exception);

            return Set.of();
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
                    .map(profileDir -> profileDir.getFileName().toString())
                    .filter(name -> !name.isBlank())
                    .map(name -> new ProfileIdentity(playerUuid, name))
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
     * file exists. A read, decode, or future-version failure is handled per
     * {@link StoragePolicy.CorruptionPolicy}.
     */
    private T loadFromDisk(ProfileIdentity identity) {
        Path path = pathFor(identity);
        if (!Files.exists(path)) return defaultValue.get();

        try {
            var result = JsonStorageIO.readAndMigrate(path, version, codec);
            if (result.migrated()) dirty.add(identity);

            return result.value();
        } catch (Exception exception) {
            Util.logError("Failed to load %s for %s".formatted(fileName, identity), exception);

            if (policy.corruption() == StoragePolicy.CorruptionPolicy.FAIL_FAST) {
                throw new IllegalStateException("Refusing to silently discard unreadable data at " + path, exception);
            }

            JsonStorageIO.quarantine(path);

            return defaultValue.get();
        }
    }

    /**
     * Writes every currently-dirty identity for this instance to disk, synchronously under
     * each identity's lock, clearing the dirty flag only when persistence succeeds.
     */
    public void flushNow() {
        for (var identity : Set.copyOf(dirty)) {
            Lock lock = locks.get(identity);
            lock.lock();
            try {
                if (!dirty.contains(identity)) continue;

                T value = cache.getIfPresent(identity);
                if (value == null) {
                    dirty.remove(identity);
                    continue;
                }

                if (saveToSystem(identity, value)) {
                    dirty.remove(identity);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean saveToSystem(ProfileIdentity identity, T value) {
        Path path = pathFor(identity);

        try {
            JsonStorageIO.writeAtomic(path, version, codec.apply(version), value);
            Util.logMessage("Saved %s for %s".formatted(fileName, identity));

            return true;
        } catch (Exception exception) {
            Util.logError("Failed to write %s for %s — your progress may not be saved".formatted(fileName, identity), exception);

            return false;
        }
    }
}
