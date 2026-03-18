package com.github.mkram17.bazaarutils.events.listener;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@LateInitModule
public final class ListenerManager {
    public static final List<BUListener> listeners = new ArrayList<>();

    public ListenerManager() {
        BazaarUtils.EVENT_BUS.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        for (BUListener listener : listeners) {
            if (listener.runOnInit && !listener.isSubscribed()) {
                listener.subscribe();
            }
        }
    }
}
