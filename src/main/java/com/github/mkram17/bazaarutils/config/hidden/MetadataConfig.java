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

    // Whether the mod has been updated with a new significant version (major or minor bump) since the last load, which can be used to trigger update-related notifications or changelogs.
    @ConfigEntry(id = "significant_version_upgrade")
    @ConfigOption.Hidden
    public static boolean SIGNIFICANT_VERSION_UPGRADE = false;

    @ConfigEntry(id = "update_notes")
    @ConfigOption.Hidden
    public static String UPDATE_NOTES = "";
}
