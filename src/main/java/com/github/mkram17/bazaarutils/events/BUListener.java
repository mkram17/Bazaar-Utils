package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.BazaarUtils;
import lombok.Getter;

import java.util.Optional;

/**
 * Base class for event listeners.
 * <p>
 * Subclasses subscribe to the event bus simply by extending this class and annotating
 * themselves {@code @Module} (or {@code @PreInitModule} / {@code @LateInitModule}). The module
 * annotation pipeline constructs the class during initialization, and this constructor calls
 * {@link #subscribe()} — which is {@code final} — so registration happens automatically; you
 * never call {@code EVENT_BUS.register} yourself. Registration is wrapped in a
 * {@link RegistrationScope} so that event predicate providers can resolve the instance being
 * registered while they build their predicates.
 * </p>
 *
 * <p>Handler methods on a listener are annotated {@code @Subscription}. See
 * {@code EVENTS_AND_HANDLERS.md} for the full picture of the event bus, the module pipeline,
 * and predicates.</p>
 *
 * <p>A subclass that additionally needs a raw Fabric callback registered at subscribe time can
 * override the no-op {@link #registerFabricEvents()} hook, as {@code JoinMessages} and
 * {@code RestrictionHelper} do.</p>
 */
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
