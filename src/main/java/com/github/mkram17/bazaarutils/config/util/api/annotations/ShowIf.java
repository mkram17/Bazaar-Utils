package com.github.mkram17.bazaarutils.config.util.api.annotations;

import com.github.mkram17.bazaarutils.config.util.api.conditions.ConfigCondition;
import com.github.mkram17.bazaarutils.config.util.api.conditions.FieldEquals;
import com.github.mkram17.bazaarutils.config.util.api.conditions.MethodEquals;

import java.lang.annotation.*;

/**
 * Declares one or more {@link ConfigCondition}s that must all evaluate to {@code true}
 * for the annotated config field to be shown in the config screen.
 *
 * <p>When multiple conditions are listed, they are combined with AND semantics:
 * all conditions must pass. For OR semantics, compose conditions explicitly inside
 * a single named class using {@link ConfigCondition#or}:
 *
 * <h3>Fail-open contract</h3>
 *
 * <p>A condition class that cannot be instantiated is treated as if it had returned
 * {@code true} — the field is shown rather than silently hidden.
 *
 * @see ConfigCondition
 * @see FieldEquals
 * @see MethodEquals
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShowIf {

    /**
     * The condition classes to evaluate. All must return {@code true} for the
     * annotated field to be shown (AND semantics).
     */
    Class<? extends ConfigCondition>[] value();
}