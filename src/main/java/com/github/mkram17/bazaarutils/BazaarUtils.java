package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.listener.ListenerManager;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.BUCommands;
import com.mojang.serialization.Codec;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import lombok.Getter;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsLateInitModules;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class BazaarUtils implements ClientModInitializer {
    public static final String MOD_ID = "bazaarutils";
    public static final String MOD_NAME = "Bazaar Utils";

    public static ModContainer SELF = FabricLoader.getInstance().getModContainer(MOD_ID).get();

    public static Configurator CONFIGURATOR = new Configurator(MOD_ID);

    public static ResourcefulConfig config = ConfigUtil.register(CONFIGURATOR);

    public static boolean updatedMajorVersion = false;

    @Getter
    private static String updateNotes;

    public static IEventBus EVENT_BUS = new EventBus();
    public static ScheduledExecutorService BUExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void onInitializeClient() {
        BazaarUtilsPreInitModules.init();

        BUCompatibilityHelper.initializePatches();

        getModProperties();
        registerEventBus();
        registerCommands();

        BazaarUtilsModules.init();

        subscribeEvents();

        BazaarUtilsLateInitModules.init();
    }

    //uses orbit for custom events
    private void registerEventBus() {
        EVENT_BUS.registerLambdaFactory("com.github.mkram17.bazaarutils", (lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
    }

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> BUCommands.register(dispatcher));
    }

    private void subscribeEvents(){
        ListenerManager.subscribeAll();
    }

    private void getModProperties(){
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(modContainer -> {
            ModMetadata metadata = modContainer.getMetadata();

            CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
            if (updateNotesValue != null)
                updateNotes = updateNotesValue.getAsString();

            var oldVersion = MetadataConfig.MOD_VERSION;
            var currentVersion = metadata.getVersion().getFriendlyString();

            var oldVersionMajor = oldVersion.substring(oldVersion.indexOf(".")+1);
            var currentVersionMajor = currentVersion.substring(currentVersion.indexOf(".")+1);

            MetadataConfig.MOD_VERSION = currentVersion;
            ConfigUtil.scheduleConfigSave();

            if (!oldVersionMajor.equals(currentVersionMajor)) {
                updatedMajorVersion = true;
            }
        });
    }
}