package com.github.mkram17.bazaarutils.config.features.gui;

import com.github.mkram17.bazaarutils.data.stored.BookmarksStorage;
import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.BookmarkUtil;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.BuyOrderAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.InstantBuyAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.amount.SellOfferAmountHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.BuyOrderPriceHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.FlipOrderPriceHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.price.SellOfferPriceHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        @ConfigButton(
                text = "bazaarutils.config.buttons.bookmarks.reset_bookmarks.runnable",
                title = "bazaarutils.config.buttons.bookmarks.reset_bookmarks.label"
        )
        public static final Runnable RESET_BOOKMARKS_BUTTON = BookmarksStorage::clear;
    }

    @Category(value = "helpers")
    @ConfigInfo(
            title = "Input Helpers",
            titleTranslation = "bazaarutils.config.buttons.helpers.category.label"
    )
    public static final class HelpersConfig {
        @ConfigEntry(id = "buy_related_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.buttons.helpers.separator.buy_related.label",
                description = "bazaarutils.config.buttons.helpers.separator.buy_related.hint"
        )
        public static boolean HELPERS_BUY_RELATED_SEPARATOR = true;

        @ConfigEntry(
                id = "instant_buy_amount",
                translation = "bazaarutils.config.buttons.helpers.instant_buy_amount.label"
        )
        public static final List<InstantBuyAmountHelper> HELPERS_INSTANT_BUY_AMOUNT_BUTTONS = new ArrayList<>(List.of(new InstantBuyAmountHelper(17)));

        @ConfigEntry(
                id = "buy_order_amount",
                translation = "bazaarutils.config.buttons.helpers.buy_order_amount.label"
        )
        public static final List<BuyOrderAmountHelper> HELPERS_BUY_ORDER_AMOUNT_BUTTONS = new ArrayList<>(List.of(new BuyOrderAmountHelper(17)));

        @ConfigEntry(
                id = "buy_order_price",
                translation = "bazaarutils.config.buttons.helpers.buy_order_price.label"
        )
        public static final List<BuyOrderPriceHelper> HELPERS_BUY_ORDER_PRICE_BUTTONS = new ArrayList<>(List.of(new BuyOrderPriceHelper(17, PricingPosition.COMPETITIVE)));

        @ConfigEntry(id = "sell_related_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.buttons.helpers.separator.sell_related.label",
                description = "bazaarutils.config.buttons.helpers.separator.sell_related.hint"
        )
        public static boolean HELPERS_SELL_RELATED_SEPARATOR = true;

        @ConfigEntry(
                id = "flip_order_price",
                translation = "bazaarutils.config.buttons.helpers.flip_order_price.label"
        )
        public static final List<FlipOrderPriceHelper> HELPERS_FLIP_ORDER_PRICE_BUTTONS = new ArrayList<>(List.of(new FlipOrderPriceHelper(17, PricingPosition.COMPETITIVE)));

        @ConfigEntry(
                id = "sell_offer_amount",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_amount.label"
        )
        public static final List<SellOfferAmountHelper> HELPERS_SELL_OFFER_AMOUNT_BUTTONS = new ArrayList<>(List.of(new SellOfferAmountHelper(17)));

        @ConfigEntry(
                id = "sell_offer_price",
                translation = "bazaarutils.config.buttons.helpers.sell_offer_price.label"
        )
        public static final List<SellOfferPriceHelper> HELPERS_SELL_OFFER_PRICE_BUTTONS = new ArrayList<>(List.of(new SellOfferPriceHelper(17, PricingPosition.COMPETITIVE)));

        public static List<SignInputHelper.TransactionAmount> amountHelpers() {
            return Stream.of(HELPERS_INSTANT_BUY_AMOUNT_BUTTONS, HELPERS_BUY_ORDER_AMOUNT_BUTTONS, HELPERS_SELL_OFFER_AMOUNT_BUTTONS)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        public static List<SignInputHelper.TransactionCost> priceHelpers() {
            return Stream.of(HELPERS_FLIP_ORDER_PRICE_BUTTONS, HELPERS_BUY_ORDER_PRICE_BUTTONS, HELPERS_SELL_OFFER_PRICE_BUTTONS)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
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
        public int size = 117;

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
                id = "item_id",
                translation = "bazaarutils.config.buttons.button.container.item_id.label"
        )
        @Comment(
                value = "The item that will be placed as the button.",
                translation = "bazaarutils.config.buttons.button.container.item_id.hint"
        )
        @ConfigOption.Renderer("bazaarutils:item")
        public String itemId = "minecraft:green_stained_glass_pane";

        @Getter
        @ConfigEntry(
                id = "slot_index",
                translation = "bazaarutils.config.buttons.button.container.slot_index.label"
        )
        @Comment(
                value = "The container slot where the button will be registered at",
                translation = "bazaarutils.config.buttons.button.container.slot_index.hint"
        )
        @ConfigOption.Range(min = 0, max = 35)
        @ConfigOption.Renderer("bazaarutils:slot")
        public int slotIndex;

        public SmallContainerButton(boolean enabled, int slotIndex) {
            this.enabled = enabled;
            this.slotIndex = slotIndex;
        }
    }
}