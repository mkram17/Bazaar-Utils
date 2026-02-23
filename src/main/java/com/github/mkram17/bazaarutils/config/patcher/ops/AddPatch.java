package com.github.mkram17.bazaarutils.config.patcher.ops;

import com.github.mkram17.bazaarutils.config.patcher.Patch;
import com.github.mkram17.bazaarutils.utils.JsonUtils;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record AddPatch(String path, JsonElement insert) implements Patch {

    public static final Identifier ID = Identifier.of("bazaarutils", "add");
    public static final MapCodec<AddPatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("path").forGetter(AddPatch::path),
            JsonUtils.JSON_ELEMENT_CODEC.fieldOf("insert").forGetter(AddPatch::insert)
    ).apply(i, AddPatch::new));

    @Override
    public Identifier id() { return ID; }

    @Override
    public void patch(JsonObject json) {
        JsonElement parent = JsonUtils.getPath(json, path.substring(0, path.lastIndexOf('.')));

        String field = path.substring(path.lastIndexOf('.') + 1);

        if (parent == null || !parent.isJsonObject()) {
            return;
        }

        JsonElement element = parent.getAsJsonObject().get(field);

        if (element instanceof JsonArray arr) {
            arr.add(insert);
        }
    }
}