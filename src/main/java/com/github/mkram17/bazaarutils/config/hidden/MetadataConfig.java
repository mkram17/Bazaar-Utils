package com.github.mkram17.bazaarutils.config.hidden;

import com.teamresourceful.resourcefulconfig.api.annotations.Category;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;

@Category("metadata_config")
@ConfigOption.Hidden
public final class MetadataConfig {
    @ConfigEntry(id = "mod_version")
    @ConfigOption.Hidden
    public static String MOD_VERSION = "";

    @ConfigEntry(id = "resources_sha")
    @ConfigOption.Hidden
    public static String RESOURCES_SHA = "";

    @ConfigEntry(id = "is_first_load")
    @ConfigOption.Hidden
    public static boolean IS_FIRST_LOAD = true;
}
