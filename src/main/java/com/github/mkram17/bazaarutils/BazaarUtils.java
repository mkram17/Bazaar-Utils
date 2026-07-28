package com.github.mkram17.bazaarutils;

import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsCommands;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsRepo;
import com.github.mkram17.bazaarutils.utils.update.UpdateUtil;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsLateInitModules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.thatgravyboat.repolib.api.RepoAPI;
import tech.thatgravyboat.repolib.api.RepoStatus;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.base.EventBus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.misc.RepoStatusEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class BazaarUtils implements ClientModInitializer {
    public static final String MOD_ID = "bazaarutils";
    public static final String MOD_NAME = "Bazaar Utils";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

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

        BazaarUtilsCommands.init();

        BazaarUtilsModules.init();

        // Persisted orders are deserialized reflectively (bypassing Order's constructor), so they
        // are not yet subscribed to the event bus — subscribe them explicitly.
        OrderUtil.subscribeLoadedOrders();

        if (RepoAPI.isInitialized()) {
            onRepoReady();
        }
    }

    private static final AtomicBoolean repoReady = new AtomicBoolean(false);

    @Subscription(event = RepoStatusEvent.class)
    public void onRepoStatus(RepoStatusEvent event) {
        if (event.getStatus() != RepoStatus.SUCCESS) {
            LOGGER.warn("SkyblockAPI repo did not load successfully (status: {}); continuing with late init anyway.", event.getStatus());
        }

        onRepoReady();
    }

    private void onRepoReady() {
        if (!repoReady.compareAndSet(false, true)) return;

        // The repo-status event is posted from repolib's async worker thread; marshal onto the
        // client thread before touching the (unsynchronized) event bus and command registration.
        Minecraft.getInstance().execute(() -> {
            ItemsRepo.buildSkyBlockItemsCache();

            BazaarUtilsLateInitModules.init();
            UpdateUtil.checkForUpdates();
        });
    }
}