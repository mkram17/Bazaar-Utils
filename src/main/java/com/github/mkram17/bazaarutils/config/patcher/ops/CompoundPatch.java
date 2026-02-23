package com.github.mkram17.bazaarutils.config.patcher.ops;

import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.config.patcher.Patch;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import java.util.List;

public record CompoundPatch(List<Patch> patches) implements Patch {

    public static final Identifier ID = Identifier.of("bazaarutils", "compound");
    public static final MapCodec<CompoundPatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ConfigPatches.CODEC.listOf().fieldOf("patches").forGetter(CompoundPatch::patches)
    ).apply(i, CompoundPatch::new));

    @Override
    public Identifier id() { return ID; }

    @Override
    public void patch(JsonObject json) {
        patches.forEach(p -> p.patch(json));
    }
}