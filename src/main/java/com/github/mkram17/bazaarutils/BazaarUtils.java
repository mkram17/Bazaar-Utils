package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.*;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsData;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import tech.thatgravyboat.repolib.api.RepoAPI;
import tech.thatgravyboat.repolib.api.RepoStatus;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.base.EventBus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnRepoStatus;
import tech.thatgravyboat.skyblockapi.api.events.misc.RepoStatusEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class BazaarUtils implements ClientModInitializer {
    public static final String MOD_ID = "bazaarutils";
    public static final String MOD_NAME = "Bazaar Utils";

    public static ModContainer SELF = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();

    public static Configurator CONFIGURATOR = new Configurator(MOD_ID);

    public static ResourcefulConfig config = ConfigUtil.register(CONFIGURATOR);

    public static boolean updatedMajorVersion = false;

    @Getter
    private static String updateNotes;

    private static boolean repoReady = false;

    public static EventBus EVENT_BUS = SkyBlockAPI.getEventBus();
    public static ScheduledExecutorService BUExecutorService = Executors.newSingleThreadScheduledExecutor();

    static {
        BazaarUtilsPreInitModules.init();
    }

    @Override
    public void onInitializeClient() {
        EVENT_BUS.register(this);

        BUCompatibilityHelper.initializePatches();

        getModProperties();

        BazaarUtilsModules.init();

        BazaarUtilsCommands.init();
        BazaarUtilsItemModifiers.init();

        if (RepoAPI.isInitialized()) {
            onRepoReady();
        }
    }

    @Subscription(event = RepoStatusEvent.class)
    @OnRepoStatus(repoStatus = RepoStatus.SUCCESS)
    private void onRepoReady(RepoStatusEvent event) {
        onRepoReady();
    }

    private void onRepoReady() {
        if (repoReady) return;
        repoReady = true;

        ItemsData.skyblockSourceReady();

        BazaarUtilsLateInitModules.init();
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