package com.github.mkram17.bazaarutils.config.patcher.ops;

import com.github.mkram17.bazaarutils.config.patcher.Patch;
import com.github.mkram17.bazaarutils.utils.JsonUtils;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import java.util.List;

public record AddListPatch(String path, List<JsonElement> inserts) implements Patch {

    public static final Identifier ID = Identifier.of("bazaarutils", "add_list");
    public static final MapCodec<AddListPatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("path").forGetter(AddListPatch::path),
            JsonUtils.JSON_ELEMENT_CODEC.listOf().fieldOf("inserts").forGetter(AddListPatch::inserts)
    ).apply(i, AddListPatch::new));

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
            inserts.forEach(arr::add);
        }
    }
}