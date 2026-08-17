package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.storage.profile.ProfileIdentity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI;
import tech.thatgravyboat.skyblockapi.helpers.McPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * One player's one SkyBlock profile — the identity Bazaar orders and flags are
 * scoped to. Bridges to the generic {@link ProfileIdentity} the storage layer
 * itself addresses by, via {@link #toIdentity()} and {@link #of}.
 */
public record ProfileKey(@NotNull UUID playerUuid, @NotNull String profileName) {
    /** The current player and profile, or empty when the profile name isn't resolvable yet. */
    public static @NotNull Optional<ProfileKey> current() {
        UUID playerUuid = McPlayer.INSTANCE.getUuid();

        String profileName = ProfileAPI.INSTANCE.getProfileName();
        if (profileName == null) return Optional.empty();

        return Optional.of(new ProfileKey(playerUuid, profileName));
    }

    /** {@code true} when this key matches whatever profile is actually active right now. */
    public boolean isCurrent() {
        return current().filter(this::equals).isPresent();
    }

    /** Converts to the generic storage-layer identity. */
    public @NotNull ProfileIdentity toIdentity() {
        return new ProfileIdentity(playerUuid, profileName);
    }

    /** The inverse of {@link #toIdentity()}. */
    public static @NotNull ProfileKey of(@NotNull ProfileIdentity identity) {
        return new ProfileKey(identity.playerUuid(), identity.profileName());
    }

    /**
     * Returns the current profile, or logs "{@code context} skipped — profile data not
     * loaded" and returns {@code null} when none is resolvable. A convenience for call
     * sites that need to bail with a clearly attributed reason rather than repeat the
     * same not-loaded check and log line themselves.
     */
    @Nullable
    public static ProfileKey requireProfile(String context) {
        return ProfileKey.current().orElseGet(() -> {
            Util.notifyError(context + " skipped — profile data not loaded", new Throwable());

            return null;
        });
    }

    @Override
    public String toString() {
        return playerUuid + ":" + profileName;
    }
}