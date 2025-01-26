package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;

import java.lang.invoke.MethodHandles;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    @Override
    public void onInitializeClient() {
        eventBus.registerLambdaFactory("com.github.sirmegabite.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Register event handlers
        ChestLoadedEvent.register();
        eventBus.subscribe(new ChestLoadedEvent());
    }
}
