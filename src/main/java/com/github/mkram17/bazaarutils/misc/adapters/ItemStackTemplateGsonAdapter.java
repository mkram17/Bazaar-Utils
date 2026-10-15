package com.github.mkram17.bazaarutils.misc.adapters;

import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStackTemplate;

import java.lang.reflect.Type;

/**
 * Stores item snapshots without accessing the item defaults that are only bound after joining a world.
 * The template codec uses the same JSON format as the previously persisted ItemStacks.
 */
public class ItemStackTemplateGsonAdapter implements JsonSerializer<ItemStackTemplate>, JsonDeserializer<ItemStackTemplate> {

    @Override
    public JsonElement serialize(ItemStackTemplate template, Type typeOfSrc, JsonSerializationContext context) {
        if (template == null) {
            return JsonNull.INSTANCE;
        }

        DataResult<JsonElement> result = ItemStackTemplate.CODEC.encodeStart(JsonOps.INSTANCE, template);

        return result.resultOrPartial(errorMessage -> {
                    Util.notifyError("Failed to serialize item template to JSON: " + errorMessage + " - Template: " + template, new Throwable());
                })
                .orElse(JsonNull.INSTANCE);
    }

    @Override
    public ItemStackTemplate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        DataResult<ItemStackTemplate> result = ItemStackTemplate.CODEC.parse(JsonOps.INSTANCE, json);

        return result.resultOrPartial(errorMessage -> {
                    Util.notifyError("Failed to deserialize item template from JSON: " + errorMessage + " - JSON: " + json, new Throwable());
                })
                .orElse(null);
    }
}
