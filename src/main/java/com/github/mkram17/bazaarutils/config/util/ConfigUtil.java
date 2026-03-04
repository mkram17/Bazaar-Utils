package com.github.mkram17.bazaarutils.config.util;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.config.util.client.ItemRendererProvider;
import com.github.mkram17.bazaarutils.config.util.client.SlotRendererProvider;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.mkram17.bazaarutils.BazaarUtils.CONFIGURATOR;


public class ConfigUtil {

    public static final Map<Integer, UnaryOperator<JsonObject>> PATCHES = ConfigPatches.loadPatches();
    public static final int VERSION = 1;
    private static boolean configSaveScheduled = false;

    public static Screen createGUI(Screen parent) {
        return ResourcefulConfigScreen.make(BazaarUtils.config)
                .withParent(parent)
                .build();
    }

    public static void openGUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen parent = client.currentScreen;
        client.send(() -> client.setScreen(createGUI(parent)));
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

    public static List<ClickableWidget> getWidgets(){
        List<ClickableWidget> widgets = new ArrayList<>();
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

        configurator.register(BUConfig.class, event ->
                PATCHES.forEach((version, patch) ->
                        event.register(version, json -> {
                            Util.logMessage("Applying patch " + version);
                            JsonObject result = patch.apply(json);
                            Util.logMessage("[BUConfig] Patch " + version + " applied successfully");
                            return result;
                        })
                )
        );

        return configurator.getConfig(BUConfig.class);
    }
}
