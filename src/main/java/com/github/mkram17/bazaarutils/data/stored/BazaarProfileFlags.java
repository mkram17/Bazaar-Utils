package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.github.mkram17.bazaarutils.utils.storage.RetentionPolicy;
import com.github.mkram17.bazaarutils.utils.storage.StoragePolicy;
import com.github.mkram17.bazaarutils.utils.storage.profile.PagedProfileStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI;

/**
 * Every small, standalone per-(player, profile) fact the mod tracks that isn't a
 * growing collection, bundled into one file rather than one file per flag.
 */
public final class BazaarProfileFlags {
    private BazaarProfileFlags() {}

    public record Data(boolean isCoop, @NotNull PlayerAccountUpgrades.BazaarFlipper bazaarFlipperTier) {
        static final Data DEFAULT = new Data(false, PlayerAccountUpgrades.BazaarFlipper.NOT_UPGRADED);

        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("isCoop").forGetter(Data::isCoop),
                PlayerAccountUpgrades.BazaarFlipper.CODEC.optionalFieldOf("bazaarFlipperTier", PlayerAccountUpgrades.BazaarFlipper.NOT_UPGRADED).forGetter(Data::bazaarFlipperTier)
        ).apply(instance, Data::new));
    }

    /** Schema version 0; eager-loaded, resident, quarantine corrupted files. */
    private static final PagedProfileStorage<Data> STORAGE = new PagedProfileStorage<>(
            0,
            "bazaar_flags",
            () -> Data.DEFAULT,
            (v) -> Data.CODEC,
            new StoragePolicy(new RetentionPolicy.Resident(), StoragePolicy.LoadPolicy.EAGER, StoragePolicy.CorruptionPolicy.QUARANTINE)
    );

    /** Returns this profile's current flags. */
    public static @NotNull Data get(@NotNull ProfileKey key) {
        return STORAGE.get(key.toIdentity());
    }

    /**
     * Returns whether {@code key} is known to be a coop profile.
     *
     * <p>When {@code key} is the profile currently active, this checks
     * {@link ProfileAPI#getCoop()} directly and, if true, records it via
     * {@link #markObservedCoop} before returning — a live read is only possible
     * for the active profile, so any other profile falls back to whatever was
     * last persisted for it.
     */
    public static boolean isKnownCoop(@NotNull ProfileKey key) {
        if (key.isCurrent() && ProfileAPI.INSTANCE.getCoop()) {
            markObservedCoop(key);
            return true;
        }

        return get(key).isCoop();
    }

    /** Records this profile as coop. A no-op once already recorded — see {@link Data}. */
    public static void markObservedCoop(@NotNull ProfileKey key) {
        STORAGE.update(key.toIdentity(), current ->
                current.isCoop() ? current : new Data(true, current.bazaarFlipperTier()));
    }

    /** Records this profile's currently observed {@code BazaarFlipper} tier. A no-op if unchanged. */
    public static void markBazaarFlipperTier(@NotNull ProfileKey key, @NotNull PlayerAccountUpgrades.BazaarFlipper tier) {
        STORAGE.update(key.toIdentity(), current ->
                current.bazaarFlipperTier() == tier ? current : new Data(current.isCoop(), tier));
    }
}