package com.github.mkram17.bazaarutils.config;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.PlayerAccountUpgrades;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import com.teamresourceful.resourcefulconfig.api.types.entries.Observable;


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
            value = "bazaarutils.config.separator.introductory.label",
            description = "bazaarutils.config.separator.introductory.hint"
    )
    public static boolean INTRODUCTORY_INFORMATION_SEPARATOR = true;

    @ConfigEntry(
            id = "advanced_configuration_mode",
            translation = "bazaarutils.config.advanced_configuration_mode.label"
    )
    @Comment(
            value = "Certain feature customization is hidden away from a normal configuration of the mod as they're options generally of no interest. Here you can toggle to see and configure them as well.",
            translation = "bazaarutils.config.advanced_configuration_mode.hint"
    )
    public static Observable<Boolean> ADVANCED_CONFIGURATION_TOGGLE = Observable.of(true);

    @ConfigEntry(
            id = "automatic_updates",
            translation = "bazaarutils.config.automatic_updates.label"
    )
    @Comment(
            value = "Automatically update the mod when an update is found.",
            translation = "bazaarutils.config.automatic_updates.hint"
    )
    public static boolean AUTOMATIC_UPDATES_TOGGLE = true;

    @ConfigEntry(
            id = "modify_indicator",
            translation = "bazaarutils.config.modify_indicator.label"
    )
    @Comment(
            value = "Items modified by Bazaar Utils will have a small indicator. Customize how it appears.",
            translation = "bazaarutils.config.modify_indicator.hint"
    )
    public static ModifyIndicator MODIFY_INDICATOR = ModifyIndicator.AT_MODIFICATION;
}