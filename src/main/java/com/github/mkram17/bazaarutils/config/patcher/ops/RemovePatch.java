package com.github.mkram17.bazaarutils.config.patcher.ops;

import com.github.mkram17.bazaarutils.config.patcher.Patch;
import com.github.mkram17.bazaarutils.utils.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record RemovePatch(String path) implements Patch {

    public static final Identifier ID = Identifier.of("bazaarutils", "remove");
    public static final MapCodec<RemovePatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("path").forGetter(RemovePatch::path)
    ).apply(i, RemovePatch::new));

    @Override
    public Identifier id() { return ID; }

    @Override
    public void patch(JsonObject json) {
        String parent = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
        String name = path.substring(path.lastIndexOf('.') + 1);

        JsonElement obj = JsonUtils.getPath(json, parent);

        if (obj != null && obj.isJsonObject()) {
            obj.getAsJsonObject().remove(name);
        }
    }
}