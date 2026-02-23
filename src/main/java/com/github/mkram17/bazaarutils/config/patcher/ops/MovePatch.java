package com.github.mkram17.bazaarutils.config.patcher.ops;

import com.github.mkram17.bazaarutils.config.patcher.Patch;
import com.github.mkram17.bazaarutils.utils.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record MovePatch(String from, String to) implements Patch {

    public static final Identifier ID = Identifier.of("bazaarutils", "move");
    public static final MapCodec<MovePatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("from").forGetter(MovePatch::from),
            Codec.STRING.fieldOf("to").forGetter(MovePatch::to)
    ).apply(i, MovePatch::new));

    @Override
    public Identifier id() { return ID; }

    @Override
    public void patch(JsonObject json) {
        JsonObject fromParent = JsonUtils.getOrCreatePath(json, from.contains(".") ? from.substring(0, from.lastIndexOf('.')) : "");

        String fromKey = from.substring(from.lastIndexOf('.') + 1);
        JsonElement value = fromParent.remove(fromKey);

        if (value == null) {
            return;
        }

        JsonObject toParent = JsonUtils.getOrCreatePath(json, to.contains(".") ? to.substring(0, to.lastIndexOf('.')) : "");
        String toKey = to.substring(to.lastIndexOf('.') + 1);

        toParent.add(toKey, value);
    }
}