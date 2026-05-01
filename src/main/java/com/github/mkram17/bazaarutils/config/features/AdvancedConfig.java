package com.github.mkram17.bazaarutils.config.features;

import com.teamresourceful.resourcefulconfig.api.annotations.Category;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;

@Category(value = "advanced_config")
@ConfigInfo(
        title = "Advanced Settings",
        titleTranslation = "bazaarutils.config.advanced.category.label",
        description = "Advanced configurations for routines & features of the mod",
        descriptionTranslation = "bazaarutils.config.advanced.category.hint",
        icon = "curly-braces"
)
public class AdvancedConfig {
    @ConfigEntry(
            id = "auto_update",
            translation = "bazaarutils.config.advanced.auto_update.label"
    )
    @Comment(
            value = "Automatically update the mod when an update is found.",
            translation = "bazaarutils.config.advanced.auto_update.hint"
    )
    public static boolean AUTO_UPDATE_TOGGLE = true;
}
