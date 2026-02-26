package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.features.gui.overlays.PriceCharts;
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
            id = "price_charts",
            translation = "bazaarutils.config.overlays.price_charts.label"
    )
    @Comment(
            value = "Injects a link to every Bazaar Items' tooltip to quick access relevant market charts.",
            translation = "bazaarutils.config.overlays.price_charts.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.overlays.separator.price_charts.label")
    public static boolean PRICE_CHARTS_TOGGLE = false;

    @ConfigEntry(
            id = "price_charts:show_outside_bazaar",
            translation = "bazaarutils.config.overlays.price_charts.show_outside_bazaar.label"
    )
    @Comment(
            value = "Whether to render the charts on items when outside of a Bazaar screen.",
            translation = "bazaarutils.config.overlays.price_charts.show_outside_bazaar.hint"
    )
    public static boolean PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR = true;

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

    @ConfigButton(
            text = "bazaarutils.config.overlays.bazaar_limits_visualizer.reset_limits.runnable",
            title = "bazaarutils.config.overlays.bazaar_limits_visualizer.reset_limits.label"
    )
    public static final Runnable RESET_LIMITS_BUTTON = () -> {
        BazaarLimitsVisualizer.limits().clear();
        BazaarLimitsVisualizer.saveLimits();
    };
}