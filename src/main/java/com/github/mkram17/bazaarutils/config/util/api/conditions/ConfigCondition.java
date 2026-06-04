package com.github.mkram17.bazaarutils.config.util.api.conditions;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;

import java.util.Optional;

/**
 * A boolean predicate over a config field's owner instance, determining whether
 * the field should be visible in the config screen.
 *
 * <p>The {@code owner} passed to {@link #shouldShow} is:
 *
 * <ul>
 *   <li>{@code Optional.of(instance)} — for fields declared on a {@code @ConfigObject}
 *       instance (e.g. an input helper whose {@code fixedAmount} field is being evaluated)
 *   <li>{@code Optional.empty()} — for static fields (e.g. top-level {@code BUConfig} fields
 *       that have no owning instance at all)
 * </ul>
 *
 * <h3>Fail-open contract</h3>
 *
 * <p>All implementations should treat an absent owner, a type mismatch, or any
 * reflection error as {@code true} — the field is shown. This prevents a broken
 * condition from silently hiding configuration from the user.
 *
 * @see FieldEquals
 * @see MethodEquals
 * @see ShowIf
 */
@FunctionalInterface
public interface ConfigCondition {
    /** ⊤ — identity for AND. A field is shown unless something hides it. */
    ConfigCondition TOP    = instance -> true;

    /** ⊥ — identity for OR. A field is always hidden (useful as a base for folding). */
    ConfigCondition BOTTOM = instance -> false;

    /**
     * Returns {@code true} if the annotated field should be shown in the config screen,
     * given the owner instance of the field.
     *
     * <p>Implementations must never throw; they must return {@code true} (fail-open)
     * in any error or indeterminate case.
     *
     * @param instance the owner of the annotated field, or {@code Optional.empty()} if
     *                 the field is static and has no owning instance
     */
    boolean shouldShow(Optional<?> instance);

    /**
     * Returns a condition that evaluates to {@code true} only when both this condition
     * and {@code other} do. Short-circuits: {@code other} is not evaluated if this
     * condition returns {@code false}.
     */
    default ConfigCondition and(ConfigCondition other) {
        return instance -> this.shouldShow(instance) && other.shouldShow(instance);
    }

    /**
     * Returns a condition that evaluates to {@code true} when either this condition
     * or {@code other} does. Short-circuits: {@code other} is not evaluated if this
     * condition returns {@code true}.
     */
    default ConfigCondition or(ConfigCondition other) {
        return instance -> this.shouldShow(instance) || other.shouldShow(instance);
    }

    /**
     * Returns a condition that evaluates to the logical negation of this condition.
     */
    default ConfigCondition negate() {
        return instance -> !this.shouldShow(instance);
    }

    /**
     * Instantiates the given condition class via its public no-arg constructor and
     * returns the result. Fails open — returns a condition that always returns
     * {@code true} — if instantiation fails for any reason.
     *
     * <p>This is the preferred way to obtain named condition instances inside composed
     * conditions, so that the fail-open behaviour is consistent regardless of which
     * class failed to load.
     *
     * @param cls a concrete {@code ConfigCondition} class with a public no-arg constructor
     */
    static ConfigCondition of(Class<? extends ConfigCondition> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return instance -> true;
        }
    }

    /**
     * foldMap over the AND-monoid.
     * ConfigCondition.all() == TOP (empty fold returns identity, correct behaviour).
     */
    @SafeVarargs
    static ConfigCondition all(Class<? extends ConfigCondition>... classes) {
        ConfigCondition acc = TOP;

        for (var cls : classes) acc = acc.and(ConfigCondition.of(cls));

        return acc;
    }

    /** foldMap over the OR-monoid. */
    @SafeVarargs
    static ConfigCondition any(Class<? extends ConfigCondition>... classes) {
        ConfigCondition acc = BOTTOM;

        for (var cls : classes) acc = acc.or(ConfigCondition.of(cls));

        return acc;
    }
}