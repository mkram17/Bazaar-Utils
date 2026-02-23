package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.inventory.InstantSellHighlight;
import com.github.mkram17.bazaarutils.features.gui.inventory.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.DoubleSellRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.SellRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.StringSellRestrictionControl;
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
        titleTranslation = "bazaarutils.config.inventory.category.value",
        description = "Configurations for the inventory features of the mod",
        descriptionTranslation = "bazaarutils.config.inventory.category.description",
        icon = "box"
)
public final class InventoryConfig {
    @ConfigEntry(
            id = "instant_sell_restrictions",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.value"
    )
    @Comment(
            value = "Enables a locking functionality over the Bazaars' §aInstant Sell§r button, with inventory state as well as action restriction.",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions.description"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.instant_sell_restrictions.value")
    public static boolean INSTANT_SELL_RESTRICTIONS_TOGGLE = true;

    @ConfigEntry(
            id = "instant_sell_restrictions:clicks_required",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions:clicks_required.value"
    )
    @Comment(
            value = "The amount of clicks required to be pressed on the §aInstant Sell§r item to allow the action.",
            translation = "bazaarutils.config.inventory.instant_sell_restrictions:clicks_required.description"
    )
    public static int INSTANT_SELL_RESTRICTIONS_CLICKS_OVERRIDE = 3;

    @ConfigEntry(
            id = "instant_sell_highlight",
            translation = "bazaarutils.config.inventory.instant_sell_highlight.value"
    )
    @Comment(
            value = "Highlights the items on your inventory that would be sold by the Bazaars' §aInstant Sell§r button.",
            translation = "bazaarutils.config.inventory.instant_sell_highlight.description"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.instant_sell_highlight.value")
    public static boolean INSTANT_SELL_HIGHLIGHT_TOGGLE = true;

    @ConfigEntry(
            id = "instant_sell_highlight:color",
            translation = "bazaarutils.config.inventory.instant_sell_highlight:color.value"
    )
    @ConfigOption.Color(
            alpha = true,
            presets = {
                    0xB2FF5555,
                    0xB2FF55FF,
                    0xB2FFFF55,
                    0xB2FFFFFF,
                    0xB2FF0000,
                    0xB2AA0000,
                    0xB255FF55,
                    0xB2AAAAAA,
                    0xB2FFAA00,
                    0xB2FFFF00
            }
    )
    public static int INSTANT_SELL_HIGHLIGHT_COLOR = 0xB2FFFF00;

    @ConfigEntry(
            id = "order_status_highlight",
            translation = "bazaarutils.config.inventory.order_status_highlight.value"
    )
    @Comment(
            value = "Highlights the status of your Bazaar Orders by colouring their item of a representative color.",
            translation = "bazaarutils.config.inventory.order_status_highlight.description"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.inventory.separator.order_status_highlight.value")
    public static boolean ORDER_STATUS_HIGHLIGHT_TOGGLE = true;

    @ConfigEntry(
            id = "order_status_highlight:competitive_color",
            translation = "bazaarutils.config.inventory.order_status_highlight:competitive_color.value"
    )
    @Comment(
            value = "The color to highlight orders which are the best offer to the market.",
            translation = "bazaarutils.config.inventory.order_status_highlight:competitive_color.description"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR = 0xFF55FF55;

    @ConfigEntry(
            id = "order_status_highlight:matched_color",
            translation = "bazaarutils.config.inventory.order_status_highlight:matched_color.value"
    )
    @Comment(
            value = "The color to highlight orders which match the market price.",
            translation = "bazaarutils.config.inventory.order_status_highlight:matched_color.description"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR = 0xFFFFFF55;

    @ConfigEntry(
            id = "order_status_highlight:outbid_color",
            translation = "bazaarutils.config.inventory.order_status_highlight:outbid_color.value"
    )
    @Comment(
            value = "The color to highlight orders which are below the market price.",
            translation = "bazaarutils.config.inventory.order_status_highlight:outbid_color.description"
    )
    @ConfigOption.Color(alpha = true)
    public static int ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR = 0xFFFF5555;

    @Category(value = "instant_sell_rules")
    @ConfigInfo(
            title = "Instant Sell Rules",
            titleTranslation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.category.value",
            description = "Manage the rules to be checked by the Instant Sell Restrictions feature",
            descriptionTranslation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.category.description",
            icon = "ruler"
    )
    public static final class SellRestrictionsRules {
        @ConfigEntry(id = "numeric_restrictions_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.inventory.instant_sell_restrictions.rules.separator.numeric_restrictions.value",
                description = "bazaarutils.config.inventory.instant_sell_restrictions.rules.separator.numeric_restrictions.description"
        )
        public static boolean NUMERIC_RESTRICTIONS_SEPARATOR = true;

        @ConfigEntry(
                id = "first_numeric_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.first_numeric_restriction.value"
        )
        public static final DoubleSellRestrictionControl FIRST_NUMERIC_RESTRICTION = new DoubleSellRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(
                id = "second_numeric_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.second_numeric_restriction.value"
        )
        public static final DoubleSellRestrictionControl SECOND_NUMERIC_RESTRICTION = new DoubleSellRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(
                id = "third_numeric_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.third_numeric_restriction.value"
        )
        public static final DoubleSellRestrictionControl THIRD_NUMERIC_RESTRICTION = new DoubleSellRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(
                id = "fourth_numeric_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.fourth_numeric_restriction.value"
        )
        public static final DoubleSellRestrictionControl FOURTH_NUMERIC_RESTRICTION = new DoubleSellRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(
                id = "fifth_numeric_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.fifth_numeric_restriction.value"
        )
        public static final DoubleSellRestrictionControl FIFTH_NUMERIC_RESTRICTION = new DoubleSellRestrictionControl(false, NumericRestrictBy.PRICE, 0);

        @ConfigEntry(id = "string_restrictions_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.inventory.instant_sell_restrictions.rules.separator.string_restrictions.value",
                description = "bazaarutils.config.inventory.instant_sell_restrictions.rules.separator.string_restrictions.description"
        )
        public static boolean STRING_RESTRICTIONS_SEPARATOR = true;

        @ConfigEntry(
                id = "first_string_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.first_string_restriction.value"
        )
        public static final StringSellRestrictionControl FIRST_STRING_RESTRICTION = new StringSellRestrictionControl(false, "");

        @ConfigEntry(
                id = "second_string_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.second_string_restriction.value"
        )
        public static final StringSellRestrictionControl SECOND_STRING_RESTRICTION = new StringSellRestrictionControl(false, "");

        @ConfigEntry(
                id = "third_string_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.third_string_restriction.value"
        )
        public static final StringSellRestrictionControl THIRD_STRING_RESTRICTION = new StringSellRestrictionControl(false, "");

        @ConfigEntry(
                id = "fourth_string_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.fourth_string_restriction.value"
        )
        public static final StringSellRestrictionControl FOURTH_STRING_RESTRICTION = new StringSellRestrictionControl(false, "");

        @ConfigEntry(
                id = "fifth_string_restriction",
                translation = "bazaarutils.config.inventory.instant_sell_restrictions.rules.fifth_string_restriction.value"
        )
        public static final StringSellRestrictionControl FIFTH_STRING_RESTRICTION = new StringSellRestrictionControl(false, "");

        public static final SellRestrictionControl<?>[] ALL = new SellRestrictionControl<?>[]{
                FIRST_NUMERIC_RESTRICTION,
                SECOND_NUMERIC_RESTRICTION,
                THIRD_NUMERIC_RESTRICTION,
                FOURTH_NUMERIC_RESTRICTION,
                FIFTH_NUMERIC_RESTRICTION,
                FIRST_STRING_RESTRICTION,
                SECOND_STRING_RESTRICTION,
                THIRD_STRING_RESTRICTION,
                FOURTH_STRING_RESTRICTION,
                FIFTH_STRING_RESTRICTION
        };

        public static List<SellRestrictionControl<?>> restrictors() {
            return Arrays.stream(ALL).collect(Collectors.toList());
        }
    }
}