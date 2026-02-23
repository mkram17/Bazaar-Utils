package com.github.mkram17.bazaarutils.config.hidden;

import com.teamresourceful.resourcefulconfig.api.annotations.Category;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;

@Category("metadata_config")
@ConfigOption.Hidden
public final class MetadataConfig {
    @ConfigEntry(
            id = "mod_version",
            translation = "bazaarutils.config.meta.mod_version.value"
    )
    @ConfigOption.Hidden
    public static String MOD_VERSION = "";

    @ConfigEntry(
            id = "resources_sha",
            translation = "bazaarutils.config.meta.resource_sha.name"
    )
    @ConfigOption.Hidden
    public static String RESOURCES_SHA = "";

    @ConfigEntry(
            id = "is_first_load",
            translation = "bazaarutils.config.meta.is_first_load.name"
    )
    @ConfigOption.Hidden
    public static boolean isFirstLoad = true;
}
