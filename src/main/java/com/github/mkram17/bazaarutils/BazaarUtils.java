package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.Events.ChatHandler;
import com.github.mkram17.bazaarutils.Events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.Utils.Commands;
import com.github.mkram17.bazaarutils.Utils.GUIUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.BazaarData;
import com.github.mkram17.bazaarutils.features.AutoOpen;
import com.github.mkram17.bazaarutils.features.customorder.CustomOrder;
import com.mojang.serialization.Codec;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.lang.invoke.MethodHandles;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus eventBus = new EventBus();
    public static GUIUtils gui = new GUIUtils();

    @Override
    public void onInitializeClient() {
        registerEvents();
        BUConfig.HANDLER.load();
        registerDeserializedEvents();
        registerCommands();
        BazaarData.scheduleBazaar();

    }

    private void registerEvents() {
        eventBus.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        ChestLoadedEvent.subscribe();
        ChatHandler.subscribe();
        gui.registerScreenEvent();
        eventBus.subscribe(new GUIUtils());
        eventBus.subscribe(new AutoOpen());
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Commands.register(dispatcher);
        });
    }
    //must be run after config load
    private void registerDeserializedEvents(){
        for(CustomOrder order : BUConfig.get().customOrders) {
            eventBus.subscribe(order);
        }
        eventBus.subscribe(BUConfig.get().autoFlipper);
        eventBus.subscribe(BUConfig.get().restrictSell);
        BUConfig.get().stashHelper.registerKeybind();
    }

    public static final ComponentType<String> CUSTOM_SIZE_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("bazaarutils", "custom_size"),
            ComponentType.<String>builder().codec(Codec.STRING).build()
    );
}
