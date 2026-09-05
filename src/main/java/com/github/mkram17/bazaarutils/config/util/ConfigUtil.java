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
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigEntryElement;
import com.teamresourceful.resourcefulconfig.client.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.mkram17.bazaarutils.BazaarUtils.CONFIGURATOR;


public class ConfigUtil {

    public static final Map<Integer, UnaryOperator<JsonObject>> PATCHES = ConfigPatches.loadPatches();
    public static final int VERSION = 1;
    private static boolean configSaveScheduled = false;

    /** {@code MetadataConfig}'s category id — internal bookkeeping that a reset must leave alone. */
    private static final String METADATA_CATEGORY = "metadata_config";

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

    /**
     * Restores every user-facing setting to the default it was declared with.
     *
     * <p>{@link #METADATA_CATEGORY} is skipped: it holds the installed version, the resource SHA and
     * the first-load flag, so clearing it would re-run first-launch behaviour and force a full
     * resource re-download.
     *
     * <p>Writes straight through rather than via {@link #scheduleConfigSave()} — a reset should be on
     * disk before the player has a chance to quit.
     */
    public static void resetToDefaults() {
        resetElementsOf(BazaarUtils.CONFIG);

        CONFIGURATOR.saveConfig(BUConfig.class);
    }

    private static void resetElementsOf(ResourcefulConfig config) {
        for (ResourcefulConfigElement element : config.elements()) {
            // Buttons and separators are their own element types, so this only touches real values.
            if (element instanceof ResourcefulConfigEntryElement entry) entry.entry().reset();
        }

        config.categories().forEach((id, category) -> {
            if (METADATA_CATEGORY.equals(id)) return;

            resetElementsOf(category);
        });
    }

    /**
     * Confirms before {@link #resetToDefaults()}, then refreshes the open settings screen so the
     * restored values are visible. The other reset buttons in this config each undo one feature;
     * this one undoes everything, so it asks first.
     */
    public static void confirmResetToDefaults() {
        Minecraft client = Minecraft.getInstance();
        Screen parent = client.screen;

        client.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) resetToDefaults();

                    client.setScreen(parent);

                    if (confirmed && parent instanceof ConfigScreen screen) screen.updateOptions();
                },
                Component.translatable("bazaarutils.config.reset_config.confirm.title"),
                Component.translatable("bazaarutils.config.reset_config.confirm.message")
        ));
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
