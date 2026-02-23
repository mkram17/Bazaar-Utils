package com.github.mkram17.bazaarutils.utils;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;

public final class JsonUtils {
    public static final Codec<JsonElement> JSON_ELEMENT_CODEC = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(JsonElement input, DynamicOps<T> ops, T prefix) {
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, input));
        }

        @Override
        public <T> DataResult<Pair<JsonElement, T>> decode(DynamicOps<T> ops, T input) {
            return DataResult.success(Pair.of(ops.convertTo(JsonOps.INSTANCE, input), ops.empty()));
        }
    };

    public static JsonElement getPath(JsonObject root, String path) {
        if (path == null || path.isEmpty()) return root;

        JsonElement current = root;

        for (var part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }

        return current;
    }

    public static JsonObject getOrCreatePath(JsonObject root, String path) {
        if (path == null || path.isEmpty()) return root;

        JsonObject current = root;

        for (var part : path.split("\\.")) {
            if (!current.has(part) || !current.get(part).isJsonObject()) {
                current.add(part, new JsonObject());
            }
            current = current.getAsJsonObject(part);
        }

        return current;
    }

    private JsonUtils() {}
}