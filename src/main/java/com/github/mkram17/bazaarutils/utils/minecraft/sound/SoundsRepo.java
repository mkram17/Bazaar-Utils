package com.github.mkram17.bazaarutils.utils.minecraft.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only view over the {@link SoundEvent} registry, used by the sound
 * picker renderer to list, resolve, and validate configurable sound ids.
 * Mirrors {@code ItemsRepo}'s role for the item picker.
 */
public final class SoundsRepo {

    private static List<SoundEvent> CACHE;

    private SoundsRepo() {}

    /** All registered sound events, sorted by resource location. */
    public static List<SoundEvent> getSounds() {
        if (CACHE == null) {
            CACHE = BuiltInRegistries.SOUND_EVENT.stream()
                    .sorted(Comparator.comparing(SoundsRepo::identify))
                    .toList();
        }

        return CACHE;
    }

    /**
     * All registered sounds whose id's first path segment matches one of the given
     * categories (e.g. {@code "entity"}, {@code "block"}, {@code "ui"}, {@code "music"}).
     * No categories (null/empty) returns everything, same as {@link #getSounds()}.
     * <p>
     * This rides on vanilla's own sound-id naming convention rather than a real registry
     * tag, so it's a heuristic — most vanilla/datapack sounds follow it, but it isn't
     * enforced anywhere.
     */
    public static List<SoundEvent> getSounds(String... categories) {
        if (categories == null || categories.length == 0) return getSounds();

        Set<String> wanted = Arrays.stream(categories)
                .map(c -> c.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return getSounds().stream()
                .filter(sound -> wanted.contains(categoryOf(sound)))
                .toList();
    }

    private static String categoryOf(SoundEvent event) {
        Identifier location = BuiltInRegistries.SOUND_EVENT.getKey(event);
        if (location == null) return "";

        String path = location.getPath();
        int dot = path.indexOf('.');
        return dot >= 0 ? path.substring(0, dot) : path;
    }

    /** Resolves a stored id (e.g. {@code "minecraft:block.note_block.bell"}) to its event, or null if unregistered/invalid. */
    public static SoundEvent resolve(String id) {
        if (id == null || id.isBlank()) return null;

        Identifier location = Identifier.tryParse(id);
        if (location == null) return null;

        return BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null);
    }

    /** Inverse of {@link #resolve(String)} — the id string to persist for a given event. */
    public static String identify(SoundEvent event) {
        if (event == null) return "";

        Identifier location = BuiltInRegistries.SOUND_EVENT.getKey(event);
        return location != null ? location.toString() : "";
    }
}