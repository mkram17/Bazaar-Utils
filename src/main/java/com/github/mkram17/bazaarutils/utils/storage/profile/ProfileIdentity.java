package com.github.mkram17.bazaarutils.utils.storage.profile;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** One (player, profile) pair — the key every storage primitive in this package addresses by. */
public record ProfileIdentity(@NotNull UUID playerUuid, @NotNull String profileName) {
    /** For log lines only — not a serialization format. */
    @Override
    public String toString() {
        return playerUuid + ":" + profileName;
    }
}