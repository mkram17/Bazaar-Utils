package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Utils.Commands;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.CustomOrder;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.item.Items;

import java.lang.invoke.MethodHandles;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    public static GUIUtils gui = new GUIUtils();
    @Override
    public void onInitializeClient() {

        BUConfig.HANDLER.load();
        registerCommands();
        registerEvents();
    }

    private void registerEvents(){
        eventBus.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        CustomOrder maxBuyOrder = new CustomOrder(() -> BUConfig.buyMaxEnabled, () -> 71680, () -> 17, Items.PURPLE_STAINED_GLASS_PANE);
        ChestLoadedEvent.registerScreenEvent();
        gui.registerScreenEvent();
        eventBus.subscribe(maxBuyOrder);
        eventBus.subscribe(new GUIUtils());
    }
    private void registerCommands(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Commands.register(dispatcher);
        });
    }
}
