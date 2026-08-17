package com.github.mkram17.bazaarutils.utils.storage;

import org.jetbrains.annotations.NotNull;

/** How and when this storage engine's data gets persisted, loaded, and recovered from corruption. */
public record StoragePolicy(
        @NotNull RetentionPolicy retention,
        @NotNull LoadPolicy load,
        @NotNull CorruptionPolicy corruption
) {
    /** Resident retention, eager loading, and quarantine-on-corruption. */
    public static final StoragePolicy DEFAULT = new StoragePolicy(new RetentionPolicy.Resident(), LoadPolicy.EAGER, CorruptionPolicy.QUARANTINE);

    /** Whether known identities are paged in only on first touch, or all warmed in the background at construction. {@link #DEFAULT} uses {@link #EAGER}. */
    public enum LoadPolicy {
        LAZY,
        EAGER
    }

    /** How a corrupted persisted record is handled on load. */
    public enum CorruptionPolicy {
        QUARANTINE,
        FAIL_FAST
    }
}