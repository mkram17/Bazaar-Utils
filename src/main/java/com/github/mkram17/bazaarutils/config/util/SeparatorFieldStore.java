package com.github.mkram17.bazaarutils.config.util;

import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigSeparatorElement;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps each {@link ResourcefulConfigSeparatorElement} to the {@link Field} it was
 * built from and the owner instance in scope at parse time.
 *
 * @see com.github.mkram17.bazaarutils.mixin.JavaConfigParserMixin
 */
public final class SeparatorFieldStore {
    private SeparatorFieldStore() {}

    public record Context(Field field, Optional<Object> owner) {}

    // <p>Uses {@link IdentityHashMap} because {@code ParsedSeparator} is a record —
    // value-based equals/hashCode would alias two separators with identical text.
    private static final Map<ResourcefulConfigSeparatorElement, Context> REGISTRY = Collections.synchronizedMap(new IdentityHashMap<>());

    public static void put(ResourcefulConfigSeparatorElement sep, Field field, Optional<Object> owner) {
        REGISTRY.put(sep, new Context(field, owner));
    }

    public static Optional<Context> get(ResourcefulConfigSeparatorElement sep) {
        return Optional.ofNullable(REGISTRY.get(sep));
    }
}