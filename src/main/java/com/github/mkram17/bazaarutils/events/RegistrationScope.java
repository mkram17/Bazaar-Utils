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
     * Seeds the context with the given instance, executes the registration action,
     * then guarantees cleanup via finally — preventing context pollution on the thread
     * if registration throws mid-way.
     */
    public static void wrap(Object instance, Runnable action) {
        CURRENT.set(new RegistrationScope(instance));

        try {
            action.run();
        } finally {
            CURRENT.remove();
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