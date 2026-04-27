package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import lombok.Getter;

import java.util.Optional;

/**
 * Interface for event listeners.
 * <p>
 * This interface defines the contract for classes that need to subscribe to the event bus
 * and handle mod events. Implementing classes should register their event handlers in the
 * {@link #subscribe()} method.
 * </p>
 * 
 * <p><strong>Important:</strong> Implementations must be added to the serialized events list
 * to be persisted with the config, unless there is a singleton object as instance data in
 * {@link BUConfig}, which gets automatically subscribed.</p>
 */
//TODO switch to using fabric event system with annotation processor
public abstract class BUListener implements AbstractListener {
    @Getter
    private transient boolean isSubscribed = false;

    public BUListener(){
        subscribe();
    }

    /**
     * Subscribes this listener to the event bus.
     * This method should register all event handlers for this listener.
     */
    @Override
    public final void subscribe(){
        if (isSubscribed) return;
        else isSubscribed = true;

        registerFabricEvents();
        RegistrationScope.wrap(this, this::subscribeToSkyblockApiEventBus);
    }

    protected void registerFabricEvents() {}

    private void subscribeToSkyblockApiEventBus() {
        BazaarUtils.EVENT_BUS.register(this);
    }
}
