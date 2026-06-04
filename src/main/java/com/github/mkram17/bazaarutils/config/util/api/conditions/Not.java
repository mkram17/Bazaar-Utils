package com.github.mkram17.bazaarutils.config.util.api.conditions;

import java.util.Optional;

/**
 * A {@link ConfigCondition} that inverts a delegate condition.
 * Subclass with a no-arg constructor, override {@link #condition()} —
 * the result is a named class usable in {@code @ShowIf}.
 */
public abstract class Not implements ConfigCondition {
    protected abstract Class<? extends ConfigCondition> condition();

    @Override
    public final boolean shouldShow(Optional<?> instance) {
        return !ConfigCondition.of(condition()).shouldShow(instance);
    }
}