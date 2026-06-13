package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.features.Bookmark;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.BUCommands;
import com.github.mkram17.bazaarutils.utils.Util;
import com.mojang.serialization.Codec;
import lombok.Getter;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class BazaarUtils implements ClientModInitializer {
    public static IEventBus EVENT_BUS = new EventBus();
    public static ArrayList<KeyMapping> keybinds = new ArrayList<>();
    public static final String MODID = "bazaarutils";
    public static final String MOD_NAME = "Bazaar Utils";
    public static boolean updatedMajorVersion = false;
    @Getter
    private static String updateNotes;
    public static ScheduledExecutorService BUExecutorService = Executors.newSingleThreadScheduledExecutor();

    public static DataComponentType<String> CUSTOM_SIZE_COMPONENT;
    public static DataComponentType<Boolean> CUSTOM_SHOWPRICECHART_COMPONENT;


    @Override
    public void onInitializeClient() {
        registerDataComponents();

        BUConfig.HANDLER.load();

        BUCompatibilityHelper.initializePatches();

        getModProperties();
        registerEventBus();
        subscribeEvents();
        registerCommands();
        setDefaultValues();
    }

    private static void registerDataComponents() {
        CUSTOM_SIZE_COMPONENT = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(BazaarUtils.MODID, "custom_size"),
                DataComponentType.<String>builder().persistent(Codec.STRING).build()
        );
        CUSTOM_SHOWPRICECHART_COMPONENT = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(BazaarUtils.MODID, "has_price_chart"),
                DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build()
        );
    }

    //uses orbit for custom events
    private void registerEventBus() {
        EVENT_BUS.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
    }

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            BUCommands.register(dispatcher);
        });
    }

    //must be run after config load
    private void subscribeEvents(){
        for(BUListener listener : BUListener.getEventListeners()) {
            listener.subscribe();
        }
    }

    private void setDefaultValues(){
        //causes errors if done as default in config bc constructor uses other config info which isnt loaded yet
        if(BUConfig.get().bookmarks.isEmpty()) {
            BUConfig.get().bookmarks.add(new Bookmark("Diamond")); // Default
        }


    }
    private void getModProperties(){
        FabricLoader.getInstance().getModContainer(MODID).ifPresent(modContainer -> {
            ModMetadata metadata = modContainer.getMetadata();

            CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
            if (updateNotesValue != null)
                updateNotes = updateNotesValue.getAsString();

            var oldVersion = BUConfig.get().MOD_VERSION;
            var currentVersion = metadata.getVersion().getFriendlyString();

            var oldVersionMajor = oldVersion.substring(oldVersion.indexOf(".")+1);
            var currentVersionMajor = currentVersion.substring(currentVersion.indexOf(".")+1);

            BUConfig.get().MOD_VERSION = currentVersion;
            Util.scheduleConfigSave();

            if(!oldVersionMajor.equals(currentVersionMajor))
                updatedMajorVersion = true;
        });
    }
}