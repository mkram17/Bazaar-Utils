package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.StringRestrictionControl;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Category(
        value = "inventory_config",
        categories = {
                InventoryConfig.SellRestrictionsRules.class
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
            id = "restrictions",
            translation = "bazaarutils.config.inventory.restrictions.label"
    )
    @Comment(
            value = "Locks selected Bazaar buttons based on inventory or action criteria to prevent accidental market actions.",
            translation = "bazaarutils.config.inventory.restrictions.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.restrictions.label")
    public static boolean RESTRICTIONS_TOGGLE = true;

    @ConfigEntry(
            id = "restrictions:features",
            translation = "bazaarutils.config.inventory.restrictions.features.label"
    )
    @Comment(
            value = "The inventory buttons for which restrictions are enabled.",
            translation = "bazaarutils.config.inventory.restrictions.features.hint"
    )
    public static RestrictionTarget[] RESTRICTIONS_ENABLED_FEATURES = new RestrictionTarget[] {};

    @ConfigEntry(
            id = "restrictions:clicks_required",
            translation = "bazaarutils.config.inventory.restrictions.clicks_required.label"
    )
    @Comment(
            value = "The number of clicks required on the feature button to confirm the action.",
            translation = "bazaarutils.config.inventory.restrictions.clicks_required.hint"
    )
    public static int RESTRICTIONS_CLICKS_OVERRIDE = 3;

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

    @Category(value = "restrictions_rules")
    @ConfigInfo(
            title = "Restrictions Rules",
            titleTranslation = "bazaarutils.config.inventory.restrictions.rules.category.label",
            description = "Manage the rules to be checked by the Inventory Restrictions feature",
            descriptionTranslation = "bazaarutils.config.inventory.restrictions.rules.category.hint",
            icon = "ruler"
    )
    public static final class SellRestrictionsRules {

        @ConfigEntry(id = "numeric_restrictions_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.inventory.restrictions.rules.separator.numeric_restrictions.label",
                description = "bazaarutils.config.inventory.restrictions.rules.separator.numeric_restrictions.hint"
        )
        public static boolean NUMERIC_RESTRICTIONS_SEPARATOR = true;

        // translation keys now use numeric index for array nesting in json5
        @ConfigEntry(id = "first_numeric_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.numeric_restriction.1.label")
        public static final DoubleRestrictionControl FIRST_NUMERIC_RESTRICTION  = new DoubleRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "second_numeric_restriction", translation = "bazaarutils.config.inventory.restrictions.rules.numeric_restriction.2.label")
        public static final DoubleRestrictionControl SECOND_NUMERIC_RESTRICTION = new DoubleRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "third_numeric_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.numeric_restriction.3.label")
        public static final DoubleRestrictionControl THIRD_NUMERIC_RESTRICTION  = new DoubleRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "fourth_numeric_restriction", translation = "bazaarutils.config.inventory.restrictions.rules.numeric_restriction.4.label")
        public static final DoubleRestrictionControl FOURTH_NUMERIC_RESTRICTION = new DoubleRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "fifth_numeric_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.numeric_restriction.5.label")
        public static final DoubleRestrictionControl FIFTH_NUMERIC_RESTRICTION  = new DoubleRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "string_restrictions_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.inventory.restrictions.rules.separator.string_restrictions.label",
                description = "bazaarutils.config.inventory.restrictions.rules.separator.string_restrictions.hint"
        )
        public static boolean STRING_RESTRICTIONS_SEPARATOR = true;

        // translation keys now use numeric index for array nesting in json5
        @ConfigEntry(id = "first_string_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.string_restriction.1.label")
        public static final StringRestrictionControl FIRST_STRING_RESTRICTION  = new StringRestrictionControl(false, "");

        @ConfigEntry(id = "second_string_restriction", translation = "bazaarutils.config.inventory.restrictions.rules.string_restriction.2.label")
        public static final StringRestrictionControl SECOND_STRING_RESTRICTION = new StringRestrictionControl(false, "");

        @ConfigEntry(id = "third_string_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.string_restriction.3.label")
        public static final StringRestrictionControl THIRD_STRING_RESTRICTION  = new StringRestrictionControl(false, "");

        @ConfigEntry(id = "fourth_string_restriction", translation = "bazaarutils.config.inventory.restrictions.rules.string_restriction.4.label")
        public static final StringRestrictionControl FOURTH_STRING_RESTRICTION = new StringRestrictionControl(false, "");

        @ConfigEntry(id = "fifth_string_restriction",  translation = "bazaarutils.config.inventory.restrictions.rules.string_restriction.5.label")
        public static final StringRestrictionControl FIFTH_STRING_RESTRICTION  = new StringRestrictionControl(false, "");

        public static final RestrictionControl<?>[] ALL = new RestrictionControl<?>[]{
                FIRST_NUMERIC_RESTRICTION, SECOND_NUMERIC_RESTRICTION, THIRD_NUMERIC_RESTRICTION,
                FOURTH_NUMERIC_RESTRICTION, FIFTH_NUMERIC_RESTRICTION,
                FIRST_STRING_RESTRICTION, SECOND_STRING_RESTRICTION, THIRD_STRING_RESTRICTION,
                FOURTH_STRING_RESTRICTION, FIFTH_STRING_RESTRICTION
        };

        public static List<RestrictionControl<?>> restrictors(RestrictionTarget target) {
            return Arrays.stream(ALL)
                    .filter(rule -> rule.appliesTo(target))
                    .collect(Collectors.toList());
        }
    }
}