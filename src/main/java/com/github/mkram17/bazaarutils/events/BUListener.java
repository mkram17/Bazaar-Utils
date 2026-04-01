package com.github.mkram17.bazaarutils.events;

import lombok.Getter;

import java.util.Optional;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Interface for event listeners.
 * <p>
 * This interface defines the contract for classes that need to subscribe to the event bus
 * and handle mod events. Implementing classes should register their event handlers in the
 * {@link #subscribe()} method.
 * </p>
 */
//TODO switch to using fabric event system with annotation processor
public abstract class BUListener implements AbstractListener {
    /**
     * Holds the listener instance being registered for the duration of EVENT_BUS.register().
     *
     * EventPredicateProvider.getPredicate(Method) only receives the method, never the instance.
     * By seeding this context immediately before registration — where all predicates are built
     * synchronously — providers like ToggleableFeaturePredicateProvider can close over the
     * actual concrete instance, rather than relying on the declaring class. The declaring class
     * would be wrong for abstract base classes with multiple concrete subclasses registered
     * simultaneously, such as RestrictionHelper.
     */
    static final class RegistrationContext extends ThreadLocal<Object> {
        /**
         * Seeds the context with the given instance, executes the registration action,
         * then guarantees cleanup via finally — preventing context pollution on the thread
         * if registration throws mid-way.
         */
        void wrap(Object instance, Runnable action) {
            set(instance);
            try {
                action.run();
            } finally {
                remove();
            }
        }
    }

    static final RegistrationContext REGISTRATION_CONTEXT = new RegistrationContext();

    public static Optional<Object> currentRegistration() {
        return Optional.ofNullable(REGISTRATION_CONTEXT.get());
    }


    @Getter
    private transient boolean isSubscribed = false;

    public BUListener() {
        subscribe();
    }

    /**
     * Subscribes this listener to the event bus.
     * This method should register all event handlers for this listener.
     */
    @Override
    public final void subscribe() {
        if (isSubscribed) {
            return;
        }

        isSubscribed = true;

        registerFabricEvents();

        REGISTRATION_CONTEXT.wrap(this, () -> EVENT_BUS.register(this));
    }

    protected void registerFabricEvents() {}
}
