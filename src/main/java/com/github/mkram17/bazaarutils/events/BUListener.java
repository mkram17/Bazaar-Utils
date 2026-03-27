package com.github.mkram17.bazaarutils.events;

import lombok.Getter;

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
        EVENT_BUS.register(this);
    }

    protected void registerFabricEvents() {}
}
