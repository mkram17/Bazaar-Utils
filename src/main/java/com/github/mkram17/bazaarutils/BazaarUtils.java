package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsRepo;
import com.github.mkram17.bazaarutils.utils.update.UpdateUtil;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsLateInitModules;
import tech.thatgravyboat.repolib.api.RepoAPI;
import tech.thatgravyboat.repolib.api.RepoStatus;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.base.EventBus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnRepoStatus;
import tech.thatgravyboat.skyblockapi.api.events.misc.RepoStatusEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class BazaarUtils implements ClientModInitializer {
    public static final String MOD_ID = "bazaarutils";
    public static final String MOD_NAME = "Bazaar Utils";

    public static final ModContainer MOD_CONTAINER = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();

    public static final Configurator CONFIGURATOR = new Configurator(MOD_ID);

    public static final ResourcefulConfig CONFIG = ConfigUtil.register(CONFIGURATOR);

    public static EventBus EVENT_BUS = SkyBlockAPI.getEventBus();
    public static ScheduledExecutorService BUExecutorService = Executors.newSingleThreadScheduledExecutor();

    static {
        BazaarUtilsPreInitModules.init();
    }

    @Override
    public void onInitializeClient() {
        EVENT_BUS.register(this);

        BUCompatibilityHelper.initializePatches();

        UpdateUtil.updateModProperties();

        BazaarUtilsModules.init();

        BazaarUtilsCommands.init();

        if (RepoAPI.isInitialized()) {
            onRepoReady();
        }
    }

    private static final AtomicBoolean repoReady = new AtomicBoolean(false);

    @Subscription(event = RepoStatusEvent.class)
    @OnRepoStatus(repoStatus = RepoStatus.SUCCESS)
    public void onRepoReady(RepoStatusEvent event) {
        onRepoReady();
    }

    private void onRepoReady() {
        if (!repoReady.compareAndSet(false, true)) return;

        ItemsRepo.buildSkyBlockItemsCache();

        BazaarUtilsLateInitModules.init();
        UpdateUtil.checkForUpdates();
    }
}