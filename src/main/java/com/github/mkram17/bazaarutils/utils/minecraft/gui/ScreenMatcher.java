package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;

/**
 * Developer-side declaration of which {@link BazaarScreenType}s a module is designed for.
 *
 * <p>This is not user configuration. User configuration lives in
 * {@link ScreenConstrained#getTargetScreens()},
 * which overrides this matcher when the user has configured a preference.
 *
 * <p>Backed by {@link EnumSet} for O(1) membership and natural enum ordering.
 * All matching routes through {@link ScreenContext#is} → {@link ScreenType#includes},
 * so eager group types (e.g. {@code CATALOG}) correctly cover their concrete members.
 */
public final class ScreenMatcher<T extends Enum<T> & ScreenType>
        implements Comparable<ScreenMatcher<T>> {

    private static final Comparator<ScreenMatcher<?>> COMPARATOR = Comparator
            .comparingInt((ScreenMatcher<?> m) -> m.anyMode ? 1 : 0)
            .thenComparingInt(m -> m.included.size())
            .thenComparingInt(m -> m.excluded.size());

    private final Class<T> enumClass;
    private final EnumSet<T> included;
    private final EnumSet<T> excluded;
    private final boolean anyMode;

    private ScreenMatcher(Class<T> enumClass, EnumSet<T> included, EnumSet<T> excluded, boolean anyMode) {
        this.enumClass = enumClass;
        this.included = included;
        this.excluded = excluded;
        this.anyMode = anyMode;
    }

    public static <T extends Enum<T> & ScreenType> ScreenMatcher<T> any(Class<T> enumClass) {
        return new ScreenMatcher<>(enumClass,
                EnumSet.noneOf(enumClass),
                EnumSet.noneOf(enumClass),
                true);
    }

    public static <T extends Enum<T> & ScreenType> ScreenMatcher<T> of(Class<T> enumClass, EnumSet<T> included) {
        return new ScreenMatcher<>(enumClass, included, EnumSet.noneOf(enumClass), false);
    }

    /**
     * Returns a new matcher that also excludes the given types.
     * Exclusions override inclusions and group membership.
     *
     * <p>Uses {@link Sets#union} to merge with any previously declared exclusions
     * without mutating either set.
     */
    @SafeVarargs
    public final ScreenMatcher<T> except(T first, T... rest) {
        return withExcluded(EnumSet.of(first, rest));
    }

    public ScreenMatcher<T> except(T[] types) {
        if (types.length == 0) return this;
        return withExcluded(EnumSet.copyOf(Arrays.asList(types)));
    }

    private ScreenMatcher<T> withExcluded(EnumSet<T> next) {
        return new ScreenMatcher<>(enumClass, included, EnumSet.copyOf(Sets.union(excluded, next)), anyMode);
    }

    public boolean matches(@Nullable ScreenContext context) {
        if (context == null) return false;

        for (T forbidden : excluded) {
            if (context.is(forbidden)) return false;
        }

        if (anyMode) return context.type().map(enumClass::isInstance).orElse(false);

        for (T allowed : included) {
            if (context.is(allowed)) return true;
        }

        return false;
    }

    public boolean matches(ContainerLoadedEvent event) {
        return matches(event.asContext());
    }

    public boolean matchesCurrent() {
        return matches(ScreenManager.getInstance().currentOrNull());
    }

    /** Defensive copy. Empty when {@link #isAnyMode()}. */
    public EnumSet<T> includesAsEnumSet() {
        return included.isEmpty() ? EnumSet.noneOf(enumClass) : EnumSet.copyOf(included);
    }

    /** Defensive copy. Empty when {@link #isAnyMode()}. */
    public EnumSet<T> excludesAsEnumSet() {
        return excluded.isEmpty() ? EnumSet.noneOf(enumClass) : EnumSet.copyOf(excluded);
    }

    public boolean isAnyMode() {
        return anyMode;
    }

    @Override
    public int compareTo(@NotNull ScreenMatcher<T> other) {
        return COMPARATOR.compare(this, other);
    }

    // ── Object ────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenMatcher<?> m)) return false;
        return anyMode == m.anyMode && included.equals(m.included) && excluded.equals(m.excluded);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(anyMode, included, excluded);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("mode", anyMode ? "ANY" : "OF")
                .add("type", enumClass.getSimpleName())
                .add("included", anyMode ? null : included)
                .add("excluded", excluded.isEmpty() ? null : excluded)
                .toString();
    }
}