package com.github.mkram17.bazaarutils.config.features;

import com.teamresourceful.resourcefulconfig.api.annotations.Category;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;

@Category(value = "website_config")
@ConfigInfo(
        title = "Website Sync",
        titleTranslation = "bazaarutils.config.website.category.label",
        description = "Link your Minecraft account to the Bazaar Utils website and sync your orders to it",
        descriptionTranslation = "bazaarutils.config.website.category.hint",
        icon = "globe"
)
public final class WebsiteConfig {

    @ConfigEntry(id = "website_separator")
    @ConfigOption.Hidden
    @ConfigOption.Separator(
            value = "bazaarutils.config.website.separator.label",
            description = "bazaarutils.config.website.separator.hint"
    )
    public static boolean WEBSITE_SEPARATOR = true;

    @ConfigEntry(
            id = "sync_orders",
            translation = "bazaarutils.config.website.sync_orders.label"
    )
    @Comment(
            value = """
                    Upload a snapshot of your bazaar orders to the website whenever you open the §6Manage Orders§r menu.

                    Nothing is sent until you link an account with §e/bu link <code>§r, and turning this off stops uploads without unlinking.
                    """,
            translation = "bazaarutils.config.website.sync_orders.hint"
    )
    public static boolean SYNC_ORDERS_TOGGLE = true;
}
