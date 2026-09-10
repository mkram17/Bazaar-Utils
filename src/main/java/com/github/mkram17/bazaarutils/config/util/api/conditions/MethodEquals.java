package com.github.mkram17.bazaarutils.config.util.api.conditions;

import com.google.common.base.Preconditions;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link ConfigCondition} that shows a field only when a typed accessor applied
 * to the owner instance returns a value equal to an expected value.
 *
 * <p>Unlike {@link FieldEquals}, this class uses no reflection: the accessor is a
 * plain {@link Function} — typically a method reference.
 *
 * <h3>Fail-open contract</h3>
 *
 * <p>Returns {@code true} (show the field) when:
 * <ul>
 *   <li>the owner is absent (the annotated field is static and has no owning instance)
 *   <li>the owner is present but is not an instance of {@code type}
 * </ul>
 *
 * @param <O> the expected runtime type of the owner instance
 * @param <T> the return type of the accessor
 * @see FieldEquals
 */
public class MethodEquals<O, T> implements ConfigCondition {

    private final Class<O> type;
    private final Function<O, T> accessor;
    private final T expected;

    /**
     * Constructs a new {@code MethodEquals} condition.
     *
     * @param type     the expected runtime type of the owner; owners that are not an
     *                 instance of this type cause the condition to fail open; must not
     *                 be {@code null}
     * @param accessor a function that extracts the comparable value from a typed owner
     *                 instance; typically a method reference; must not be {@code null}
     *                 and must not throw
     * @param expected the value the accessor must return; {@code null} is valid and
     *                 checks that the accessor itself returns {@code null}
     */
    public MethodEquals(Class<O> type, Function<O, T> accessor, T expected) {
        this.type     = Preconditions.checkNotNull(type,     "type");
        this.accessor = Preconditions.checkNotNull(accessor, "accessor");
        this.expected = expected;
    }

    @Override
    public final boolean shouldShow(Optional<?> instance) {
        return instance
                .filter(type::isInstance)
                .map(type::cast)
                .map(accessor)
                .map(v -> Objects.equals(expected, v))
                .orElse(true);
    }
}