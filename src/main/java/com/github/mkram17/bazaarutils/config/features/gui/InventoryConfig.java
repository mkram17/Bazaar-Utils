package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.StringRestrictionControl;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Category(
        value = "inventory_config",
        categories = {
                InventoryConfig.RestrictionRules.class
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
            id = "instant_sell_highlight",
            translation = "bazaarutils.config.inventory.instant_sell_highlight.label"
    )
    @Comment(
            value = "Highlights the items on your inventory that would be sold by the Bazaars' §aInstant Sell§r button.",
            translation = "bazaarutils.config.inventory.instant_sell_highlight.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.instant_sell_highlight.label")
    public static boolean INSTANT_SELL_HIGHLIGHT_TOGGLE = true;

    @ConfigEntry(
            id = "instant_sell_highlight:color",
            translation = "bazaarutils.config.inventory.instant_sell_highlight.color.label"
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
            translation = "bazaarutils.config.inventory.order_status_highlight.label"
    )
    @Comment(
            value = "Highlights the status of your Bazaar Orders by colouring their item of a representative color.",
            translation = "bazaarutils.config.inventory.order_status_highlight.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.order_status_highlight.label")
    public static boolean ORDER_STATUS_HIGHLIGHT_TOGGLE = true;

    @ConfigEntry(
            id = "order_status_highlight:filled_color",
            translation = "bazaarutils.config.inventory.order_status_highlight.filled_color.label"
    )
    @Comment(
            value = "The color to highlight orders which have been fully filled and are awaiting claim.",
            translation = "bazaarutils.config.inventory.order_status_highlight.filled_color.hint"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_FILLED_COLOR = 0xFFEEEEEE;

    @ConfigEntry(
            id = "order_status_highlight:competitive_color",
            translation = "bazaarutils.config.inventory.order_status_highlight.competitive_color.label"
    )
    @Comment(
            value = "The color to highlight orders which are the best offer to the market.",
            translation = "bazaarutils.config.inventory.order_status_highlight.competitive_color.hint"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR = 0xFF55FF55;

    @ConfigEntry(
            id = "order_status_highlight:matched_color",
            translation = "bazaarutils.config.inventory.order_status_highlight.matched_color.label"
    )
    @Comment(
            value = "The color to highlight orders which match the market price.",
            translation = "bazaarutils.config.inventory.order_status_highlight.matched_color.hint"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR = 0xFFFFFF55;

    @ConfigEntry(
            id = "order_status_highlight:outbid_color",
            translation = "bazaarutils.config.inventory.order_status_highlight.outbid_color.label"
    )
    @Comment(
            value = "The color to highlight orders which are below the market price.",
            translation = "bazaarutils.config.inventory.order_status_highlight.outbid_color.hint"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR = 0xFFFF5555;

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
        public static RestrictionTarget[] RESTRICTIONS_ENABLED_FEATURES = new RestrictionTarget[]{};

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