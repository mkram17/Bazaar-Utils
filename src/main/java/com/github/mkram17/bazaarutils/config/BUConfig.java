package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.GeneralDataConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.FeatureConfig;
import com.github.mkram17.bazaarutils.config.features.KeybindConfig;
import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.features.gui.buttons.Bookmarks;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;

import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.mkram17.bazaarutils.BazaarUtils.MOD_ID;

@Config(
        value =  MOD_ID + "/config",
        categories = {
                GeneralDataConfig.class,
                MetadataConfig.class,
                ChatConfig.class,
                ButtonsConfig.class,
                InventoryConfig.class,
                OverlaysConfig.class,
                NotificationsConfig.class,
                DeveloperConfig.class
        },
        version = BUConfig.VERSION
)
@ConfigInfo(
        title = "Bazaar Utils",
        description = "A QOL mod for Hypixel Skyblock focused on the bazaar.",
        links = {
                @ConfigInfo.Link(
                        value = "https://modrinth.com/mod/bazaar-utils",
                        icon = "modrinth",
                        text = "Modrinth"
                )
        }
)
public final class BUConfig {

    private static final BUConfig INSTANCE = new BUConfig();

    public static BUConfig get(){
        return INSTANCE;
    }

    @ConfigEntry(id = "introductory_separator")
    @ConfigOption.Hidden
    @ConfigOption.Separator(
            value = "bazaarutils.config.separator.introductory.value",
            description = "bazaarutils.config.separator.introductory.description"
    )
    public static boolean INTRODUCTORY_INFORMATION_SEPARATOR = true;

    @ConfigEntry(
            id = "user_bazaar_tax",
            translation = "bazaarutils.config.user_bazaar_tax.value"
    )
    @Comment(
            value = "The bazaar tax percentage of your current profile considering all possible upgrades.",
            translation = "bazaarutils.config.user_bazaar_tax.description"
    )
    public static double userBazaarTax = 1.125;

    public GeneralDataConfig general = new GeneralDataConfig();
    public MetadataConfig metadata = new MetadataConfig();
    public DeveloperConfig developer = new DeveloperConfig();
    public FeatureConfig feature = new FeatureConfig();
    public KeybindConfig keybind = new KeybindConfig();

    public static final Map<Integer, UnaryOperator<JsonObject>> PATCHES = ConfigPatches.loadPatches();
    public static final int VERSION = 1;

    public static ResourcefulConfig register(Configurator configurator) {
        if (PATCHES.size() + 1 != VERSION) {
            throw new IllegalStateException(
                    "BUConfig VERSION (" + VERSION + ") is out of sync with patch count " +
                            "— expected VERSION = " + (PATCHES.size() + 1)
            );
        }

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
