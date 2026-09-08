package com.github.mkram17.bazaarutils.config.util;

import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps separators and buttons to the {@link Field} they were built from and the
 * owner instance in scope at parse time, so their visibility conditions can be evaluated.
 *
 * @see com.github.mkram17.bazaarutils.mixin.JavaConfigParserMixin
 */
public final class ConfigElementFieldStore {
    private ConfigElementFieldStore() {}

    public record Context(Field field, Optional<Object> owner) {}

    // Parsed separators and buttons are records; use identity so equal elements
    // from different fields can still have different visibility conditions.
    private static final Map<ResourcefulConfigElement, Context> REGISTRY = Collections.synchronizedMap(new IdentityHashMap<>());

    public static void put(ResourcefulConfigElement element, Field field, Optional<Object> owner) {
        REGISTRY.put(element, new Context(field, owner));
    }

    public static Optional<Context> get(ResourcefulConfigElement element) {
        return Optional.ofNullable(REGISTRY.get(element));
    }
}
