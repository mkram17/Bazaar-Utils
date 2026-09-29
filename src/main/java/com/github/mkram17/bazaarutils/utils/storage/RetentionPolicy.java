package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.utils.storage.profile.PagedProfileStorage;

/** How long a loaded profile's data stays resident in {@link PagedProfileStorage} once paged in. */
public sealed interface RetentionPolicy {
    /** Never evicted once loaded. */
    record Resident() implements RetentionPolicy {}

    /** Evictable once more than {@code maxEntries} identities are resident at once. */
    record Bounded(long maxEntries) implements RetentionPolicy {}
}