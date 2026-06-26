package com.github.mkram17.bazaarutils.misc.adapters;

import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;

public class ItemStackCodecGsonAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

    @Override
    public JsonElement serialize(ItemStack stack, Type typeOfSrc, JsonSerializationContext context) {
        if (stack == null || stack.isEmpty()) {
            return JsonNull.INSTANCE;
        }

        RegistryOps<JsonElement> ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        DataResult<JsonElement> result = ItemStack.CODEC.encodeStart(ops, stack);

        return result.resultOrPartial(errorMessage -> {
                    Util.notifyError("Failed to serialize ItemStack to JSON: " + errorMessage + " - Stack: " + stack, new Throwable());
                })
                .orElse(JsonNull.INSTANCE);
    }

    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return ItemStack.EMPTY;
        }

        RegistryOps<JsonElement> ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, json);

        return result.resultOrPartial(errorMessage -> {
                    Util.notifyError("Failed to deserialize ItemStack from JSON: " + errorMessage + " - JSON: " + json.toString(), new Throwable());
                })
                .orElse(ItemStack.EMPTY);
    }
}
