package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.config.features.SerializableListTestConfig;
import com.github.mkram17.bazaarutils.config.hidden.GeneralDataConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.FeatureConfig;
import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.config.util.api.SerializableListElement;
import com.github.mkram17.bazaarutils.config.util.client.SerializableListRenderer;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.mkram17.bazaarutils.BazaarUtils.MOD_ID;

@Config(
        value =  MOD_ID + "/config",
        categories = {
                SerializableListTestConfig.class,
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
            id = "bazaar_flipper_account_upgrade",
            translation = "bazaarutils.config.bazaar_flipper_account_upgrade.value"
    )
    @Comment(
            value = """
                    The tier of your §6Bazaar Flipper§r Account Upgrade, which is factored in on tax calculation and current order limits.
                    
                    Your Bazaar Flipper level can be upgraded by talking to §dElizabeth§r, at the §bCommunity Center§r, in the §bSkyBlock Hub§r.
                    """,
            translation = "bazaarutils.config.bazaar_flipper_account_upgrade.description"
    )
    public static PlayerAccountUpgrades.BazaarFlipper USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE = PlayerAccountUpgrades.BazaarFlipper.NOT_UPGRADED;

    public GeneralDataConfig general = new GeneralDataConfig();
    public FeatureConfig feature = new FeatureConfig();

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

        ResourcefulConfig config = configurator.getConfig(BUConfig.class);

        ResourcefulConfigUI.registerElementRenderer(
                Identifier.of("serializablelist"),
                element -> {
                    SerializableListElement sle = SerializableListElement.wrap(element, config);
                    return sle != null ? new SerializableListRenderer(sle) : null;
                }
        );

        return config;
    }
}
