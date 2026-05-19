package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

@Category(value = "overlays_config")
@ConfigInfo(
        title = "Overlays Config",
        titleTranslation = "bazaarutils.config.overlays.category.label",
        description = "Configurations for the overlays to be created by the mod",
        descriptionTranslation = "bazaarutils.config.overlays.category.hint",
        icon = "sidebar"
)
public class OverlaysConfig {
    @ConfigEntry(
            id = "bazaar_limits_visualizer",
            translation = "bazaarutils.config.overlays.bazaar_limits_visualizer.label"
    )
    @Comment(
            value = """
            Adds informational text to Bazaar Screens about the status of your daily Bazaar Limits.

            The Bazaar limits each profile to order/offer tradeables for up to 15,000,000,000.00 coins each day.
            """,
            translation = "bazaarutils.config.overlays.bazaar_limits_visualizer.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.overlays.separator.bazaar_limits_visualizer.label")
    public static boolean BAZAAR_LIMITS_VISUALIZER_TOGGLE = true;
}