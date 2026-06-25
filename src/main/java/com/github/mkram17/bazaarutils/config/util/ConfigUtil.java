package com.github.mkram17.bazaarutils.config.util;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.config.util.client.ItemRendererProvider;
import com.github.mkram17.bazaarutils.config.util.client.SlotRendererProvider;
import com.github.mkram17.bazaarutils.config.util.client.SoundRendererProvider;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.mkram17.bazaarutils.BazaarUtils.CONFIGURATOR;


public class ConfigUtil {
    private static final BazaarLogger LOG = BazaarLogger.of(ConfigUtil.class);

    public static final Map<Integer, UnaryOperator<JsonObject>> PATCHES = ConfigPatches.loadPatches();
    public static final int VERSION = 1;
    private static boolean configSaveScheduled = false;

    public static Screen createGUI(Screen parent) {
        return ResourcefulConfigScreen.make(BazaarUtils.CONFIG)
                .withParent(parent)
                .build();
    }

    public static void openGUI() {
        Minecraft client = Minecraft.getInstance();
        Screen parent = client.screen;
        client.schedule(() -> client.setScreen(createGUI(parent)));
    }

    public static void scheduleConfigSave() {
        if (!configSaveScheduled) {
            configSaveScheduled = true;

            Util.tickExecuteLater(20, () -> { // 1 second
                CONFIGURATOR.saveConfig(BUConfig.class);
                configSaveScheduled = false;
            });
        }
    }

    public static List<AbstractWidget> getWidgets(){
        List<AbstractWidget> widgets = new ArrayList<>();
        //automatically added using @RegisterWidget annotation
        return widgets;
    }

    public static ResourcefulConfig register(Configurator configurator) {
        if (PATCHES.size() + 1 != VERSION) {
            throw new IllegalStateException(
                    "BUConfig VERSION (" + VERSION + ") is out of sync with patch count " +
                            "— expected VERSION = " + (PATCHES.size() + 1)
            );
        }
        
        ItemRendererProvider.register();
        SlotRendererProvider.register();
        SoundRendererProvider.register();

        configurator.register(BUConfig.class, event ->
                PATCHES.forEach((version, patch) ->
                        event.register(version, json -> {
                            LOG.info("Applying config patch v{}", version);
                            JsonObject result = patch.apply(json);
                            LOG.info("Config patch v{} applied", version);

                            return result;
                        })
                )
        );

        return configurator.getConfig(BUConfig.class);
    }
}
