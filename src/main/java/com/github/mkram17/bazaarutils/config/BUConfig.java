package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.FeatureConfig;
import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.patcher.ConfigPatches;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import static com.github.mkram17.bazaarutils.BazaarUtils.MOD_ID;

@Config(
        value = MOD_ID + "/config",
        categories = {
                MetadataConfig.class,
                ChatConfig.class,
                ButtonsConfig.class,
                InventoryConfig.class,
                OverlaysConfig.class,
                NotificationsConfig.class,
                DeveloperConfig.class
        },
        version = ConfigUtil.VERSION
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

    public FeatureConfig feature = new FeatureConfig();

}