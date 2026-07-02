package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent screen-type dispatch modeled after {@link Optional}.
 *
 * State is encoded in a single {@code payload} field — exactly as Optional
 * encodes presence in its {@code value} field:
 * <ul>
 *   <li>{@code null}  — unmatched (no branch has fired)
 *   <li>{@code VOID}  — matched by a side-effecting branch (no return value)
 *   <li>{@code R}     — matched by a value-returning branch
 * </ul>
 *
 * Each {@code when()} either returns {@code this} (guard didn't match, or
 * already matched) or a new matched instance.
 */
@SuppressWarnings("unchecked")
public final class ScreenSwitch<R> {
    /** Sentinel: a branch fired but produced no value (side-effecting). */
    private static final Object VOID = new Object();

    /** Sentinel: no context available — all branches skip. */
    private static final ScreenSwitch<?> NO_CONTEXT = new ScreenSwitch<>(null, null);

    private final @Nullable ScreenContext context;

    /**
     * {@code null} → unmatched
     * {@code VOID} → matched, no value
     * anything else → matched, cast to R
     */
    private final @Nullable Object payload;

    private ScreenSwitch(@Nullable ScreenContext context, @Nullable Object payload) {
        this.context = context;
        this.payload = payload;
    }

    public static <R> ScreenSwitch<R> on(@Nullable ScreenContext context) {
        return context == null
                ? (ScreenSwitch<R>) NO_CONTEXT
                : new ScreenSwitch<>(context, null);
    }

    public static <R> ScreenSwitch<R> on(@NotNull ContainerLoadedEvent event) {
        return on(event.asContext());
    }

    public static <R> ScreenSwitch<R> onCurrent() {
        return on(ScreenManager.getInstance().currentOrNull());
    }

    public boolean isMatched() {
        return payload != null;
    }

    private boolean hasValue() {
        return payload != null && payload != VOID;
    }

    public ScreenSwitch<R> when(ScreenType type, Function<ScreenContext, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        if (payload != null || context == null || !context.is(type)) return this;

        return new ScreenSwitch<>(context, mapper.apply(context));
    }

    public <T extends Enum<T> & ScreenType> ScreenSwitch<R> when(ScreenMatcher<T> matcher, Function<ScreenContext, ? extends R> mapper) {
        Objects.requireNonNull(matcher);
        Objects.requireNonNull(mapper);
        if (payload != null || !matcher.matches(context)) return this;

        return new ScreenSwitch<>(context, mapper.apply(context));
    }

    public ScreenSwitch<R> then(ScreenType type, Consumer<ScreenContext> action) {
        Objects.requireNonNull(action);
        if (payload != null || context == null || !context.is(type)) return this;

        action.accept(context);

        return new ScreenSwitch<>(context, VOID);
    }

    public <T extends Enum<T> & ScreenType> ScreenSwitch<R> then(ScreenMatcher<T> matcher, Consumer<ScreenContext> action) {
        Objects.requireNonNull(matcher);
        Objects.requireNonNull(action);
        if (payload != null || !matcher.matches(context)) return this;

        action.accept(context);

        return new ScreenSwitch<>(context, VOID);
    }

    public Optional<R> toOptional() {
        return hasValue() ? Optional.of((R) payload) : Optional.empty();
    }

    public R orElse(@Nullable R fallback) {
        return hasValue() ? (R) payload : fallback;
    }

    public R orElseGet(Supplier<? extends R> supplier) {
        Objects.requireNonNull(supplier);

        return hasValue() ? (R) payload : supplier.get();
    }

    public void orElse(Runnable action) {
        Objects.requireNonNull(action);

        if (payload == null) action.run();
    }

    public void orElse(Consumer<@Nullable ScreenContext> action) {
        Objects.requireNonNull(action);

        if (payload == null) action.accept(context);
    }

    public R orElseThrow() {
        if (!hasValue()) throw new NoSuchElementException("No matching screen type");

        return (R) payload;
    }

    public <X extends Throwable> R orElseThrow(Supplier<? extends X> supplier) throws X {
        Objects.requireNonNull(supplier);
        if (!hasValue()) throw supplier.get();

        return (R) payload;
    }

    public ScreenSwitch<R> ifMatched(Consumer<? super R> action) {
        Objects.requireNonNull(action);
        if (hasValue()) action.accept((R) payload);

        return this;
    }

    public <U> Optional<U> map(Function<? super R, ? extends U> mapper) {
        Objects.requireNonNull(mapper);

        return toOptional().map(mapper);
    }

    public <U> Optional<U> flatMap(Function<? super R, Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);

        return toOptional().flatMap(mapper);
    }

    @Override
    public String toString() {
        if (payload == null)  return "ScreenSwitch.unmatched";
        if (payload == VOID)  return "ScreenSwitch.matched[void]";

        return "ScreenSwitch.matched[" + payload + "]";
    }
}