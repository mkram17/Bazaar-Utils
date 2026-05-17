package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.github.mkram17.bazaarutils.config.util.api.conditions.AdvancedConfigurationMode;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.StringRestrictionControl;
import com.github.mkram17.bazaarutils.utils.minecraft.item.SlotHighlight;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Category(
        value = "inventory_config",
        categories = {
                InventoryConfig.RestrictionRules.class,
                InventoryConfig.Highlights.class
        }
)
@ConfigInfo(
        title = "Inventory Config",
        titleTranslation = "bazaarutils.config.inventory.category.label",
        description = "Configurations for the inventory features of the mod",
        descriptionTranslation = "bazaarutils.config.inventory.category.hint",
        icon = "box"
)
public final class InventoryConfig {
    @ConfigEntry(
            id = "dim_non_bazaar_items",
            translation = "bazaarutils.config.inventory.dim_non_bazaar_items.label"
    )
    @Comment(
            value = "When on the Bazaar, dims items in your inventory that cannot be traded on the Bazaar.",
            translation = "bazaarutils.config.inventory.dim_non_bazaar_items.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.dim_non_bazaar_items.label")
    public static boolean DIM_NON_BAZAAR_ITEMS_TOGGLE = true;

    @ConfigEntry(
            id = "price_charts",
            translation = "bazaarutils.config.inventory.price_charts.label"
    )
    @Comment(
            value = "Injects a link to every Bazaar Items' tooltip to quick access relevant market charts.",
            translation = "bazaarutils.config.inventory.price_charts.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.price_charts.label")
    public static boolean PRICE_CHARTS_TOGGLE = false;

    @ConfigEntry(
            id = "price_charts:show_outside_bazaar",
            translation = "bazaarutils.config.inventory.price_charts.show_outside_bazaar.label"
    )
    @Comment(
            value = "Whether to render the charts on items when outside of a Bazaar screen.",
            translation = "bazaarutils.config.inventory.price_charts.show_outside_bazaar.hint"
    )
    public static boolean PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR = true;

    @ConfigEntry(
            id = "summary_order_indicator",
            translation = "bazaarutils.config.inventory.summary_order_indicator.label"
    )
    @Comment(
            value = "When hovering over the summary of a products' price, inject information about your current bids/asks next to where they sit at.",
            translation = "bazaarutils.config.inventory.summary_order_indicator.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.summary_order_indicator.label")
    public static boolean SUMMARY_ORDER_INDICATOR_TOGGLE = true;

    @Category(value = "highlights")
    @ConfigInfo(
            title = "Inventory Highlights",
            titleTranslation = "bazaarutils.config.inventory.highlights.category.label"
    )
    public static final class Highlights {
        @ConfigEntry(
                id = "instant_sell_highlight",
                translation = "bazaarutils.config.inventory.highlights.instant_sell_highlight.label"
        )
        @Comment(
                value = "Highlights the items on your inventory that would be sold by the Bazaars' §aInstant Sell§r button.",
                translation = "bazaarutils.config.inventory.highlights.instant_sell_highlight.hint"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.inventory.highlights.separator.instant_sell_highlight.label")
        public static boolean INSTANT_SELL_HIGHLIGHT_TOGGLE = true;
        @ConfigEntry(
                id = "instant_sell_highlight:style",
                translation = "bazaarutils.config.inventory.highlights.instant_sell_highlight.style.label"
        )
        @Comment(
                value = "Determines how the highlight color is rendered on matching inventory slots.",
                translation = "bazaarutils.config.inventory.highlights.instant_sell_highlight.style.hint"
        )
        public static SlotHighlight.HighlightStyle INSTANT_SELL_HIGHLIGHT_STYLE = SlotHighlight.HighlightStyle.BACKGROUND;
        @ConfigEntry(
                id = "instant_sell_highlight:color",
                translation = "bazaarutils.config.inventory.highlights.instant_sell_highlight.color.label"
        )
        @ConfigOption.Color(
                alpha = true,
                presets = {
                        0xB2FF5555, 0xB2FF55FF, 0xB2FFFF55, 0xB2FFFFFF,
                        0xB2FF0000, 0xB2AA0000, 0xB255FF55, 0xB2AAAAAA,
                        0xB2FFAA00, 0xB2FFFF00
                }
        )
        public static int INSTANT_SELL_HIGHLIGHT_COLOR = 0xB2FFFF00;
        @ConfigEntry(
                id = "order_status_highlight",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.label"
        )
        @Comment(
                value = "Highlights the status of your Bazaar Orders by colouring their item of a representative color.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.hint"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.inventory.highlights.separator.order_status_highlight.label")
        public static boolean ORDER_STATUS_HIGHLIGHT_TOGGLE = true;
        @ConfigEntry(
                id = "order_status_highlight:style",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.style.label"
        )
        @Comment(
                value = "Determines how the status colors are rendered on your order slots.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.style.hint"
        )
        public static SlotHighlight.HighlightStyle ORDER_STATUS_HIGHLIGHT_STYLE = SlotHighlight.HighlightStyle.BACKGROUND;
        @ConfigEntry(
                id = "order_status_highlight:filled_color",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.filled_color.label"
        )
        @Comment(
                value = "The color to highlight orders which have been fully filled and are awaiting claim.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.filled_color.hint"
        )
        @ConfigOption.Color(alpha = true)
        public static int ORDER_STATUS_HIGHLIGHT_FILLED_COLOR = 0xFFEEEEEE;
        @ConfigEntry(
                id = "order_status_highlight:competitive_color",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.competitive_color.label"
        )
        @Comment(
                value = "The color to highlight orders which are the best offer to the market.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.competitive_color.hint"
        )
        @ConfigOption.Color(alpha = true)
        public static int ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR = 0xFF55FF55;
        @ConfigEntry(
                id = "order_status_highlight:matched_color",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.matched_color.label"
        )
        @Comment(
                value = "The color to highlight orders which match the market price.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.matched_color.hint"
        )
        @ConfigOption.Color(alpha = true)
        public static int ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR = 0xFFFFFF55;
        @ConfigEntry(
                id = "order_status_highlight:outbid_color",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.outbid_color.label"
        )
        @Comment(
                value = "The color to highlight orders which are below the market price.",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.outbid_color.hint"
        )
        @ConfigOption.Color(alpha = true)
        public static int ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR = 0xFFFF5555;

        @ConfigEntry(
                id = "order_status_highlight:self_outbid",
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.self_outbid.label"
        )
        @Comment(
                value = """
                    By default, your own volume at a price level doesn't count against you — so a spot you hold alone still shows as COMPETITIVE.
                    Enable this if you'd rather any shared price level report MATCHED regardless of who owns the volume.
                    """,
                translation = "bazaarutils.config.inventory.highlights.order_status_highlight.self_outbid.hint"
        )
        @ShowIf(AdvancedConfigurationMode.class)
        public static boolean ORDER_STATUS_SELF_OUTBID_TOGGLE = false;
    }

    @Category(value = "restrictions")
    @ConfigInfo(
            title = "Restrictions Rules",
            titleTranslation = "bazaarutils.config.inventory.restrictions.category.label"
    )
    public static final class RestrictionRules {
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.inventory.restrictions.enabled.label"
        )
        @Comment(
                value = "Locks selected Bazaar buttons based on inventory or action criteria to prevent accidental market actions.",
                translation = "bazaarutils.config.inventory.restrictions.enabled.hint"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.inventory.restrictions.separator.introductory.label")
        public static boolean RESTRICTIONS_TOGGLE = true;

        @ConfigEntry(
                id = "features",
                translation = "bazaarutils.config.inventory.restrictions.features.label"
        )
        @Comment(
                value = "The inventory buttons for which restrictions are enabled.",
                translation = "bazaarutils.config.inventory.restrictions.features.hint"
        )
        public static RestrictionTarget[] RESTRICTIONS_ENABLED_FEATURES = new RestrictionTarget[]{
                RestrictionTarget.INSTANT_SELL,
                RestrictionTarget.SELL_SACKS,
        };

        @ConfigEntry(
                id = "clicks_required",
                translation = "bazaarutils.config.inventory.restrictions.clicks_required.label"
        )
        @Comment(
                value = "The number of clicks required on the feature button to confirm the action.",
                translation = "bazaarutils.config.inventory.restrictions.clicks_required.hint"
        )
        public static int RESTRICTIONS_CLICKS_OVERRIDE = 3;

        @ConfigEntry(id = "rules_informational_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.inventory.restrictions.separator.rules_informational.label",
                description = "bazaarutils.config.inventory.restrictions.separator.rules_informational.hint"
        )
        public static boolean RESTRICTIONS_RULES_INFORMATIONAL_SEPARATOR = true;

        @ConfigEntry(
                id = "numeric_restrictions",
                translation = "bazaarutils.config.inventory.restrictions.numeric_restrictions.label"
        )
        @Comment(
                value = "Rules checking numeric conditions (e.g., total items or coins) to restrict targeted actions.",
                translation = "bazaarutils.config.inventory.restrictions.numeric_restrictions.hint"
        )
        public static final List<DoubleRestrictionControl> RESTRICTIONS_NUMERIC_RULES = new ArrayList<>(List.of((new DoubleRestrictionControl(NumericRestrictBy.PRICE, 0))));

        @ConfigEntry(
                id = "string_restrictions",
                translation = "bazaarutils.config.inventory.restrictions.string_restrictions.label"
        )
        @Comment(
                value = "Rules checking specific item names or types to restrict targeted actions.",
                translation = "bazaarutils.config.inventory.restrictions.string_restrictions.hint"
        )
        public static final List<StringRestrictionControl> RESTRICTIONS_STRING_RULES = new ArrayList<>();

        public static List<RestrictionControl<?>> restrictors(RestrictionTarget target) {
            return Stream.concat(RESTRICTIONS_NUMERIC_RULES.stream(), RESTRICTIONS_STRING_RULES.stream())
                    .filter(rule -> rule.appliesTo(target))
                    .collect(Collectors.toList());
        }
    }
}