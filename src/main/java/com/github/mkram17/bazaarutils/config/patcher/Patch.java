package com.github.mkram17.bazaarutils.config.patcher;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import java.util.function.UnaryOperator;

public interface Patch extends UnaryOperator<JsonObject> {
    Identifier id();

    void patch(JsonObject json);

    @Override
    default JsonObject apply(JsonObject json) {
        patch(json);
        return json;
    }
}