package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;

import java.lang.invoke.MethodHandles;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    public static GUIUtils gui = new GUIUtils();
    @Override
    public void onInitializeClient() {
        eventBus.registerLambdaFactory("com.github.sirmegabite.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        BUConfig.HANDLER.load();
        registerEvents();
    }

    private static void registerEvents(){
        ChestLoadedEvent.register();
        eventBus.subscribe(new ChestLoadedEvent());
    }
}
