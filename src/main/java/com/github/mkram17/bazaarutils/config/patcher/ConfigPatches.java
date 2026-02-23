package com.github.mkram17.bazaarutils.config.patcher;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.patcher.ops.AddPatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.AddListPatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.CompoundPatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.MovePatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.RemovePatch;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.util.Identifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;


@Slf4j
public final class ConfigPatches {
    private static final Map<Identifier, MapCodec<? extends Patch>> REGISTRY = new LinkedHashMap<>();
    public static final Codec<Patch> CODEC = Identifier.CODEC.dispatch(Patch::id, REGISTRY::get);

    static {
        register(MovePatch.ID, MovePatch.CODEC);
        register(RemovePatch.ID, RemovePatch.CODEC);
        register(CompoundPatch.ID, CompoundPatch.CODEC);
        register(AddPatch.ID, AddPatch.CODEC);
        register(AddListPatch.ID, AddListPatch.CODEC);
    }

    public static void register(Identifier id, MapCodec<? extends Patch> codec) {
        REGISTRY.put(id, codec);
    }

    public static Map<Integer, UnaryOperator<JsonObject>> loadPatches() {
        Path patchDir = BazaarUtils.SELF.findPath("repo/patches").orElse(null);

        if (patchDir == null || !Files.exists(patchDir)) {
            return Map.of();
        }

        List<Map.Entry<Integer, UnaryOperator<JsonObject>>> patches = new ArrayList<>();

        Gson gson = new Gson();

        try (Stream<Path> stream = Files.walk(patchDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                int id = Integer.parseInt(path.getFileName().toString().replaceAll("[^0-9]", ""));

                try {
                    JsonElement json = gson.fromJson(Files.readString(path), JsonObject.class);
                    Patch patch = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                    log.info("[BUConfig] Loaded patch #{} ({}) from {}", id, patch.id(), path.getFileName());
                    patches.add(Map.entry(id, patch));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load patch: " + path, e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to walk patches directory", e);
        }

        Map<Integer, UnaryOperator<JsonObject>> sorted = new LinkedHashMap<>();

        patches.stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        return sorted;
    }
}