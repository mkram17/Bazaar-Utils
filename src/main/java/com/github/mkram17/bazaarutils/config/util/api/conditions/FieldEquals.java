package com.github.mkram17.bazaarutils.config.util.api.conditions;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link ConfigCondition} that shows a field only when a named sibling field on
 * the same owner instance holds a value equal to an expected value.
 *
 * <p>The class hierarchy of the owner is walked from most-derived to least-derived,
 * so conditions defined on a supertype work correctly for subclass instances.
 *
 * <h3>Fail-open contract</h3>
 *
 * <p>Returns {@code true} (show the field) when:
 * <ul>
 *   <li>the owner is absent (the annotated field is static and has no owning instance)
 *   <li>no field with the given name exists anywhere in the class hierarchy
 *   <li>the field cannot be read due to an {@link IllegalAccessException}
 * </ul>
 *
 * @param <T> the type of the sibling field's value
 * @see MethodEquals
 */
public class FieldEquals<T> implements ConfigCondition {

    private final String fieldName;
    private final T expected;

    /**
     * Constructs a new {@code FieldEquals} condition.
     *
     * @param fieldName the exact name of the sibling field to read; must not be {@code null}
     * @param expected  the value the sibling field must equal; {@code null} is valid and
     *                  checks that the field itself holds {@code null}
     */
    public FieldEquals(String fieldName, T expected) {
        this.fieldName = Preconditions.checkNotNull(fieldName, "fieldName");
        this.expected = expected;
    }

    @Override
    public final boolean shouldShow(Optional<?> instance) {
        return instance.map(it -> {
            Field f = resolve(it.getClass());
            if (f == null) return true;

            try {
                return Objects.equals(expected, f.get(it));
            } catch (IllegalAccessException e) {
                return true; // fail-open per contract
            }
        }).orElse(true);
    }

    @Nullable
    private Field resolve(Class<?> cls) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @Nullable
    private static Object safeGet(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}