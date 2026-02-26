package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.features.gui.buttons.Bookmarks;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.BuyOrderAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.InstantBuyAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.SellOfferAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.BuyOrderPriceHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.FlipOrderPriceHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.SellOfferPriceHelper;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.SellRestrictionControl;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Category(
        value = "buttons_config",
        categories = {
                ButtonsConfig.BookmarksConfig.class,
                ButtonsConfig.HelpersConfig.class
        }
)
@ConfigInfo(
        title = "Buttons Config",
        titleTranslation = "bazaarutils.config.buttons.category.label",
        description = "Configurations for the buttons to be injected/handled by the mod",
        descriptionTranslation = "bazaarutils.config.buttons.category.hint",
        icon = "pointer"
)
public final class ButtonsConfig {

    @ConfigEntry(
            id = "open_settings",
            translation = "bazaarutils.config.buttons.open_settings.label"
    )
    @Comment(
            value = "Adds a button to selected menus/screen to quick access the mods' settings.",
            translation = "bazaarutils.config.buttons.open_settings.hint"
    )
    public static final WidgetButton OPEN_SETTINGS_BUTTON = new WidgetButton(true);

    @ConfigEntry(
            id = "open_orders",
            translation = "bazaarutils.config.buttons.open_orders.label"
    )
    @Comment(
            value = "Adds a button to selected menus/screen to quick access your orders page.\n\nRequires a Booster Cookie effect active in order to function.",
            translation = "bazaarutils.config.buttons.open_orders.hint"
    )
    public static final WidgetButton OPEN_ORDERS_BUTTON = new WidgetButton(true);

    @ConfigEntry(id = "container_buttons_separator")
    @ConfigOption.Hidden
    @ConfigOption.Separator(
            value = "bazaarutils.config.buttons.separator.container_buttons.label",
            description = "bazaarutils.config.buttons.separator.container_buttons.hint"
    )
    public static boolean CONTAINER_BUTTONS_SEPARATOR = true;

    @ConfigEntry(
            id = "cancel_order_and_search",
            translation = "bazaarutils.config.buttons.cancel_order_and_search.label"
    )
    @Comment(
            value = "Adds a button to an unfilled orders' (or offer) settings page to cancel it and search once again the item.",
            translation = "bazaarutils.config.buttons.cancel_order_and_search.hint"
    )
    public static final SmallContainerButton CANCEL_ORDER_AND_SEARCH = new SmallContainerButton(false, 25);

    @Category(value = "bookmarks")
    @ConfigInfo(
            title = "Bookmarks",
            titleTranslation = "bazaarutils.config.buttons.bookmarks.category.label",
            icon = "bookmark"
    )
    public static final class BookmarksConfig {

        @ConfigEntry(id = "introductory_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.buttons.bookmarks.separator.introductory.label",
                description = "bazaarutils.config.buttons.bookmarks.separator.introductory.hint"
        )
        public static boolean BOOKMARKS_INTRODUCTORY_SEPARATOR = true;

        @ConfigEntry(
                id = "open_bookmark",
                translation = "bazaarutils.config.buttons.bookmarks.open_bookmark.label"
        )
        @Comment(
                value = "Configures the button that appears on selected menus/screen to quick search the relevant bookmark.",
                translation = "bazaarutils.config.buttons.bookmarks.open_bookmark.hint"
        )
        public static final WidgetButton OPEN_BOOKMARK_BUTTON = new WidgetButton(true);

        @ConfigEntry(
                id = "toggle_bookmark",
                translation = "bazaarutils.config.buttons.bookmarks.toggle_bookmark.label"
        )
        @Comment(
                value = "Adds a button to every item's page to toggle a quick access button to search the same item on the Bazaar.",
                translation = "bazaarutils.config.buttons.bookmarks.toggle_bookmark.hint"
        )
        public static final SmallContainerButton TOGGLE_BOOKMARK_BUTTON = new SmallContainerButton(true, 0);

        @ConfigButton(
                text = "bazaarutils.config.buttons.bookmarks.reset_bookmarks.runnable",
                title = "bazaarutils.config.buttons.bookmarks.reset_bookmarks.label"
        )
        public static final Runnable RESET_BOOKMARKS_BUTTON = () -> {
            Bookmarks.bookmarks().clear();
            Bookmarks.saveBookmarks();
        };
    }

    @Category(value = "helpers")
    @ConfigInfo(
            title = "Input Helpers",
            titleTranslation = "bazaarutils.config.buttons.helpers.category.value"
    )
    public static final class HelpersConfig {
        @ConfigEntry(
                id = "buy_order_amount",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the amount for this order the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.buy_order_amount.value")
        public static boolean BUY_ORDER_AMOUNT_TOGGLE = true;

        @ConfigEntry(
                id = "first_buy_order_amount",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount:first_buy_order_amount.value"
        )
        public static final BuyOrderAmountHelper FIRST_BUY_ORDER_AMOUNT = new BuyOrderAmountHelper(true, 17);

        @ConfigEntry(
                id = "second_buy_order_amount",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount:second_buy_order_amount.value"
        )
        public static final BuyOrderAmountHelper SECOND_BUY_ORDER_AMOUNT = new BuyOrderAmountHelper(false, 8);

        @ConfigEntry(
                id = "third_buy_order_amount",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount:third_buy_order_amount.value"
        )
        public static final BuyOrderAmountHelper THIRD_BUY_ORDER_AMOUNT = new BuyOrderAmountHelper(false, 26);

        @ConfigEntry(
                id = "buy_order_price",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the price for this order the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.buy_order_price.value")
        public static boolean BUY_ORDER_PRICE_TOGGLE = true;

        @ConfigEntry(
                id = "first_buy_order_price",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price:first_buy_order_price.value"
        )
        public static final BuyOrderPriceHelper FIRST_BUY_ORDER_PRICE = new BuyOrderPriceHelper(true, 17, PricingPosition.COMPETITIVE);

        @ConfigEntry(
                id = "second_buy_order_price",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price:second_buy_order_price.value"
        )
        public static final BuyOrderPriceHelper SECOND_BUY_ORDER_PRICE = new BuyOrderPriceHelper(false, 8, PricingPosition.MATCHED);

        @ConfigEntry(
                id = "third_buy_order_price",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price:third_buy_order_price.value"
        )
        public static final BuyOrderPriceHelper THIRD_BUY_ORDER_PRICE = new BuyOrderPriceHelper(false, 26, PricingPosition.OUTBID);

        @ConfigEntry(
                id = "instant_buy_amount",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the amount for this offer the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.instant_buy_amount.value")
        public static boolean INSTANT_BUY_AMOUNT_TOGGLE = true;

        @ConfigEntry(
                id = "first_instant_buy_amount",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount:first_instant_buy_amount.value"
        )
        public static final InstantBuyAmountHelper FIRST_INSTANT_BUY_AMOUNT = new InstantBuyAmountHelper(true, 17);

        @ConfigEntry(
                id = "second_instant_buy_amount",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount:second_instant_buy_amount.value"
        )
        public static final InstantBuyAmountHelper SECOND_INSTANT_BUY_AMOUNT = new InstantBuyAmountHelper(false, 8);

        @ConfigEntry(
                id = "third_instant_buy_amount",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount:third_instant_buy_amount.value"
        )
        public static final InstantBuyAmountHelper THIRD_INSTANT_BUY_AMOUNT = new InstantBuyAmountHelper(false, 26);

        @ConfigEntry(
                id = "sell_offer_amount",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the amount for this offer the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.sell_offer_amount.value")
        public static boolean SELL_OFFER_AMOUNT_TOGGLE = true;

        @ConfigEntry(
                id = "first_sell_offer_amount",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount:first_sell_offer_amount.value"
        )
        public static final SellOfferAmountHelper FIRST_SELL_OFFER_AMOUNT = new SellOfferAmountHelper(true, 17);

        @ConfigEntry(
                id = "second_sell_offer_amount",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount:second_sell_offer_amount.value"
        )
        public static final SellOfferAmountHelper SECOND_SELL_OFFER_AMOUNT = new SellOfferAmountHelper(false, 8);

        @ConfigEntry(
                id = "third_sell_offer_amount",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount:third_sell_offer_amount.value"
        )
        public static final SellOfferAmountHelper THIRD_SELL_OFFER_AMOUNT = new SellOfferAmountHelper(false, 26);

        @ConfigEntry(
                id = "sell_offer_price",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the price for this offer the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.sell_offer_price.value")
        public static boolean SELL_OFFER_PRICE_TOGGLE = true;

        @ConfigEntry(
                id = "first_sell_offer_price",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price:first_sell_offer_price.value"
        )
        public static final SellOfferPriceHelper FIRST_SELL_OFFER_PRICE = new SellOfferPriceHelper(true, 17, PricingPosition.COMPETITIVE);

        @ConfigEntry(
                id = "second_sell_offer_price",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price:second_sell_offer_price.value"
        )
        public static final SellOfferPriceHelper SECOND_SELL_OFFER_PRICE = new SellOfferPriceHelper(false, 8, PricingPosition.MATCHED);

        @ConfigEntry(
                id = "third_sell_offer_price",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price:third_sell_offer_price.value"
        )
        public static final SellOfferPriceHelper THIRD_SELL_OFFER_PRICE = new SellOfferPriceHelper(false, 26, PricingPosition.OUTBID);

        @ConfigEntry(
                id = "flip_order_price",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price.value"
        )
        @Comment(
                value = "Places a item at the desired slot, which when clicked will input as the price for this flip the computed, configurable, value.",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price.description"
        )
        @ConfigOption.Separator(value = "bazaarutils.config.buttons.helpers.separator.flip_order_price.value")
        public static boolean FLIP_ORDER_PRICE_TOGGLE = true;

        @ConfigEntry(
                id = "first_flip_order_price",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price:first_flip_order_price.value"
        )
        public static final FlipOrderPriceHelper FIRST_FLIP_ORDER_PRICE = new FlipOrderPriceHelper(true, 17, PricingPosition.COMPETITIVE);

        @ConfigEntry(
                id = "second_flip_order_price",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price:second_flip_order_price.value"
        )
        public static final FlipOrderPriceHelper SECOND_FLIP_ORDER_PRICE = new FlipOrderPriceHelper(false, 8, PricingPosition.MATCHED);

        @ConfigEntry(
                id = "third_flip_order_price",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price:third_flip_order_price.value"
        )
        public static final FlipOrderPriceHelper THIRD_FLIP_ORDER_PRICE = new FlipOrderPriceHelper(false, 26, PricingPosition.OUTBID);

        public static final SignInputHelper.TransactionAmount[] AMOUNT_HELPERS = {
                FIRST_BUY_ORDER_AMOUNT,
                SECOND_BUY_ORDER_AMOUNT,
                THIRD_BUY_ORDER_AMOUNT,
                FIRST_INSTANT_BUY_AMOUNT,
                SECOND_INSTANT_BUY_AMOUNT,
                THIRD_INSTANT_BUY_AMOUNT,
                FIRST_SELL_OFFER_AMOUNT,
                SECOND_SELL_OFFER_AMOUNT,
                THIRD_SELL_OFFER_AMOUNT
        };

        public static List<SignInputHelper.TransactionAmount> amountHelpers() {
            return Arrays.stream(AMOUNT_HELPERS).collect(Collectors.toList());
        }

        public static final SignInputHelper.TransactionCost[] PRICE_HELPERS = {
                FIRST_BUY_ORDER_PRICE,
                SECOND_BUY_ORDER_PRICE,
                THIRD_BUY_ORDER_PRICE,
                FIRST_SELL_OFFER_PRICE,
                SECOND_SELL_OFFER_PRICE,
                THIRD_SELL_OFFER_PRICE,
                FIRST_FLIP_ORDER_PRICE,
                SECOND_FLIP_ORDER_PRICE,
                THIRD_FLIP_ORDER_PRICE
        };

        public static List<SignInputHelper.TransactionCost> priceHelpers() {
            return Arrays.stream(PRICE_HELPERS).collect(Collectors.toList());
        }
    }

    @ConfigObject
    public static final class WidgetButton {
        @Getter
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.buttons.button.widget.enabled.label"
        )
        @Comment(
                value = "Whether the button will be registered or not",
                translation = "bazaarutils.config.buttons.button.widget.enabled.hint"
        )
        public boolean enabled;

        @ConfigEntry(
                id = "size",
                translation = "bazaarutils.config.buttons.button.widget.size.label"
        )
        public int size = 18;

        @ConfigEntry(
                id = "spacing",
                translation = "bazaarutils.config.buttons.button.widget.spacing.label"
        )
        public int spacing = 4;

        public WidgetButton(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @ConfigObject
    public static final class SmallContainerButton {
        @Getter
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.buttons.button.container.enabled.label"
        )
        @Comment(
                value = "Whether the button will be registered or not",
                translation = "bazaarutils.config.buttons.button.container.enabled.hint"
        )
        public boolean enabled;

        @Getter
        @ConfigEntry(
                id = "slot_number",
                translation = "bazaarutils.config.buttons.button.container.slot_number.label"
        )
        @Comment(
                value = "The container slot where the button will be registered at",
                translation = "bazaarutils.config.buttons.button.container.slot_number.hint"
        )
        @ConfigOption.Range(min = 0, max = 35)
        public int slotNumber;

        public SmallContainerButton(boolean enabled, int slotNumber) {
            this.enabled = enabled;
            this.slotNumber = slotNumber;
        }
    }
}