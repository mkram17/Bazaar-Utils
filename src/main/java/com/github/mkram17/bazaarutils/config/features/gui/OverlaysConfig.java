package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.features.gui.overlays.PriceCharts;
import com.github.mkram17.bazaarutils.features.gui.overlays.UserOrdersOverlay;
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
    public static final Runnable RESET_LIMITS_BUTTON = BazaarLimitsVisualizer::resetLimits;

    @ConfigEntry(
            id = "user_orders_overlay",
            translation = "bazaarutils.config.overlays.user_orders_overlay.label"
    )
    @Comment(
            value = "Lists your own orders next to the main Bazaar screen, so you can read their status without opening the orders menu.",
            translation = "bazaarutils.config.overlays.user_orders_overlay.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.overlays.separator.user_orders_overlay.label")
    public static boolean USER_ORDERS_OVERLAY_TOGGLE = true;

    @ConfigEntry(
            id = "user_orders_overlay:side",
            translation = "bazaarutils.config.overlays.user_orders_overlay.side.label"
    )
    @Comment(
            value = "Which side of the Bazaar screen the list is anchored to. It is placed clear of the mod buttons and bookmarks already on that side.",
            translation = "bazaarutils.config.overlays.user_orders_overlay.side.hint"
    )
    public static UserOrdersOverlay.Side USER_ORDERS_OVERLAY_SIDE = UserOrdersOverlay.Side.RIGHT;

    @ConfigEntry(
            id = "user_orders_overlay:max_rows",
            translation = "bazaarutils.config.overlays.user_orders_overlay.max_rows.label"
    )
    @Comment(
            value = "How many orders to list. Orders that do not fit are summarised on a final line.",
            translation = "bazaarutils.config.overlays.user_orders_overlay.max_rows.hint"
    )
    @ConfigOption.Range(min = 1, max = 20)
    public static int USER_ORDERS_OVERLAY_MAX_ROWS = 8;
}