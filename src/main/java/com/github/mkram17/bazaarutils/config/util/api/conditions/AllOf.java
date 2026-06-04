package com.github.mkram17.bazaarutils.config.util.api.conditions;

import java.util.Optional;

/**
 * A {@link ConfigCondition} that shows a field only when ALL delegate conditions
 * return {@code true}. Subclass with a no-arg constructor, override
 * {@link #conditions()} — the result is a named class usable in {@code @ShowIf}.
 *
 * <p>Evaluation is delegated to {@link ConfigCondition#all}; an empty
 * {@link #conditions()} array returns {@link ConfigCondition#TOP} (field shown).
 */
public abstract class AllOf implements ConfigCondition {

    protected abstract Class<? extends ConfigCondition>[] conditions();

    @Override
    public final boolean shouldShow(Optional<?> instance) {
        return ConfigCondition.all(conditions()).shouldShow(instance);
    }
}