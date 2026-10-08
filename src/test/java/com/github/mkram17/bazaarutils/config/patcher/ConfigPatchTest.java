package com.github.mkram17.bazaarutils.config.patcher;

import com.github.mkram17.bazaarutils.config.patcher.ops.CompoundPatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.MovePatch;
import com.github.mkram17.bazaarutils.config.patcher.ops.RemovePatch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPatchTest {
    @Test
    void movingANestedSettingPreservesItsValueAndUnrelatedSettings() {
        var json = json("""
                {"old":{"rule":{"amount":100,"targets":["SELL_SACKS"]},"keep":42},"other":{"enabled":true}}
                """);
        var value = json.getAsJsonObject("old").get("rule").deepCopy();
        var unrelated = json.get("other").deepCopy();
        new MovePatch("old.rule", "inventory.restrictions.rule").patch(json);
        assertFalse(json.getAsJsonObject("old").has("rule"));
        assertEquals(42, json.getAsJsonObject("old").get("keep").getAsInt());
        assertEquals(unrelated, json.get("other"));
        assertEquals(value, json.getAsJsonObject("inventory").getAsJsonObject("restrictions").get("rule"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"old.missing", "missing.nested.rule", "other.enabled.rule"})
    void movingAMissingSettingDoesNotChangeTheConfig(String from) {
        var json = json("""
                {"old":{"keep":42},"other":{"enabled":true}}
                """);
        var before = json.deepCopy();
        assertDoesNotThrow(() -> new MovePatch(from, "inventory.rule").patch(json));
        assertEquals(before, json);
    }

    @Test
    void removingMissingFieldsIsSafeAndPreservesOtherSettings() {
        var json = json("""
                {"old":{"keep":42}}
                """);
        var before = json.deepCopy();
        new RemovePatch("old.missing").patch(json);
        new RemovePatch("missing.nested.rule").patch(json);
        assertEquals(before, json);
    }

    @Test
    void compoundPatchesRunInTheDeclaredOrder() {
        var json = json("""
                {"old":{"amount":123},"keep":true}
                """);
        var compound = new CompoundPatch(List.of(
                new MovePatch("old.amount", "intermediate.amount"),
                new MovePatch("intermediate.amount", "inventory.threshold"),
                new RemovePatch("intermediate")));
        assertSame(json, compound.apply(json));
        assertEquals(123, json.getAsJsonObject("inventory").get("threshold").getAsInt());
        assertFalse(json.getAsJsonObject("old").has("amount"));
        assertFalse(json.has("intermediate"));
        assertTrue(json.get("keep").getAsBoolean());
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
