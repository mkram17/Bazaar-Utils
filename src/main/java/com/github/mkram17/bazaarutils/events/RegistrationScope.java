package com.github.mkram17.bazaarutils.events;

import java.util.Optional;

/**
 * Holds the listener instance being registered for the duration/the stack of the instance created.
 *
 * EventPredicateProvider.getPredicate(Method) only receives the method, never the instance.
 * By seeding this context immediately before registration — where all predicates are built
 * synchronously — providers like ToggleableFeaturePredicateProvider can close over the
 * actual concrete instance, rather than relying on the declaring class. The declaring class
 * would be wrong for abstract base classes with multiple concrete subclasses registered
 * simultaneously, such as RestrictionHelper.
 *
 *
 */
public final class RegistrationScope {
    private static final ThreadLocal<RegistrationScope> CURRENT = new ThreadLocal<>();

    private final Object instance;

    private RegistrationScope(Object instance) {
        this.instance = instance;
    }

    /**
     * Seeds the context with the given instance, executes the registration action, then restores
     * whatever scope was previously in effect — guaranteed via finally, so a registration that
     * throws mid-way cannot leave a stale instance on the thread.
     *
     * <p>This saves and restores rather than simply clearing, so that nested calls behave. If
     * registering one listener causes another to be constructed — a static initializer reached
     * while {@code EventBus.register} resolves method parameter types, or a module built lazily
     * from a registration path — the inner {@code wrap} would otherwise clear the context on exit,
     * leaving the outer listener's remaining predicates to be built with no scope at all. That
     * outer listener would then register partially and throw
     * {@code "predicate built outside of registration context"}.</p>
     */
    public static void wrap(Object instance, Runnable action) {
        RegistrationScope previous = CURRENT.get();
        CURRENT.set(new RegistrationScope(instance));

        try {
            action.run();
        } finally {
            // At the outermost level remove() rather than set(null), so no stale entry is left
            // behind in the thread's ThreadLocalMap.
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static Optional<RegistrationScope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public Object getInstance() {
        return instance;
    }

    public <T> Optional<T> as(Class<T> type) {
        return type.isInstance(instance) ? Optional.of(type.cast(instance)) : Optional.empty();
    }
}