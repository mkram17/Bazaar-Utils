package com.github.mkram17.bazaarutils.config.util.api.conditions;

import java.util.Optional;

/**
 * A {@link ConfigCondition} that shows a field when ANY delegate condition
 * returns {@code true}. Subclass with a no-arg constructor, override
 * {@link #conditions()} — the result is a named class usable in {@code @ShowIf}.
 *
 * <p>Each delegate is instantiated via {@link ConfigCondition#of}, so fail-open
 * behaviour is inherited from each condition's own contract. An empty
 * {@link #conditions()} array returns {@link ConfigCondition#BOTTOM} (field hidden).
 */
public abstract class AnyOf implements ConfigCondition {
    protected abstract Class<? extends ConfigCondition>[] conditions();

    @Override
    public final boolean shouldShow(Optional<?> instance) {
        return ConfigCondition.any(conditions()).shouldShow(instance);
    }
}