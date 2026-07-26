package com.github.mkram17.bazaarutils.events;

public interface AbstractListener {
    /**
     * Subscribes this listener to the event bus.
     * This method should register all event handlers for this listener.
     */
    void subscribe();
}
