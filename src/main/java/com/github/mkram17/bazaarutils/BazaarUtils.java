package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Events.EventHandler;
import com.github.mkram17.bazaarutils.Utils.Commands;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.features.AutoFlipper;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import java.lang.invoke.MethodHandles;

import static com.github.mkram17.bazaarutils.config.BUConfig.maxBuyOrder;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    public static GUIUtils gui = new GUIUtils();
    @Override
    public void onInitializeClient() {
        BUConfig.HANDLER.load();
        registerCommands();
        registerEvents();
        BazaarData.scheduleBazaar();
    }

    private void registerEvents(){
        eventBus.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        ChestLoadedEvent.subscribe();
        EventHandler.subscribe();
        gui.registerScreenEvent();
        eventBus.subscribe(maxBuyOrder);
        eventBus.subscribe(new GUIUtils());
        eventBus.subscribe(new AutoFlipper());
    }

    private void registerCommands(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Commands.register(dispatcher);
        });
    }
}
