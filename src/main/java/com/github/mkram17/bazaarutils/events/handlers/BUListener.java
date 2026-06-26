package com.github.mkram17.bazaarutils.events.handlers;

import com.github.mkram17.bazaarutils.config.BUConfig;

import java.util.List;

//TODO switch to using fabric event system with annotation processor
public interface BUListener {
    //must add to getTransientEvents()
    void subscribe();

    static List<BUListener> getEventListeners(){
        return BUConfig.get().getSerializedEvents();
    }
}
