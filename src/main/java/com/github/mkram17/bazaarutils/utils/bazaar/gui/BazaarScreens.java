package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;

import java.util.*;

public class BazaarScreens {
    private static boolean initialized = false;

    private BazaarScreens() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        for (ScreenType screen : ALL) {
            ScreenManager.register(screen);
        }
    }

//    Screens

    public static final ScreenType MAIN_PAGE = new ScreenType.Builder()
            .name("MAIN_PAGE")
            .genericContainer()
            .containerTitle("Bazaar ➜ ")
            .build();

    public static final ScreenType SETTINGS_PAGE = new ScreenType.Builder()
            .name("SETTINGS_PAGE")
            .genericContainer()
            .containerTitle("Settings")
            .build();

    public static final ScreenType ORDERS_PAGE = new ScreenType.Builder()
            .name("ORDERS_PAGE")
            .genericContainer()
            .containerTitle("Bazaar Orders")
            .build();

//    Browsing stuff

    public static final ScreenType ITEM_PAGE = new ScreenType.Builder()
            .name("ITEM_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("VIEW_GRAPHS", BazaarSlots.ITEM_PAGE.VIEW_GRAPHS::query)
            .build();

    public static final ScreenType ITEMS_GROUP_PAGE = new ScreenType.Builder()
            .name("ITEMS_GROUP_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("SWITCH_VIEW_MODE", BazaarSlots.ITEMS_GROUP_PAGE.SWITCH_VIEW_MODE::query)
            .build();

//    Buying stuff

    public static final ScreenType BUY_ORDER_AMOUNT = new ScreenType.Builder()
            .name("BUY_ORDER_AMOUNT")
            .genericContainer()
            .containerTitle("How many do you want?")
            .build();

    public static final ScreenType BUY_ORDER_PRICE = new ScreenType.Builder()
            .name("BUY_ORDER_PRICE")
            .genericContainer()
            .containerTitle("How much do you want to pay?")
            .build();

    public static final ScreenType BUY_ORDER_CONFIRMATION = new ScreenType.Builder()
            .name("BUY_ORDER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Buy Order")
            .build();

    public static final ScreenType PENDING_BUY_ORDER_OPTIONS = new ScreenType.Builder()
            .name("PENDING_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_UNFILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_UNFILLED_BUY_ORDER::query)
            .build();

    public static final ScreenType COMPLETED_BUY_ORDER_OPTIONS = new ScreenType.Builder()
            .name("COMPLETED_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_FILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_FILLED_BUY_ORDER::query)
            .build();

    public static final ScreenType INSTANT_BUY = new ScreenType.Builder()
            .name("INSTANT_BUY")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("INPUT_CUSTOM_AMOUNT", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT::query)
            .build();

//    Selling stuff

    public static final ScreenType SELL_OFFER_AMOUNT = new ScreenType.Builder()
            .name("SELL_OFFER_AMOUNT")
            .genericContainer()
            .containerTitle("How many are you selling?")
            .build();

    public static final ScreenType SELL_OFFER_PRICE = new ScreenType.Builder()
            .name("SELL_OFFER_PRICE")
            .genericContainer()
            .containerTitle("At what price are you selling?")
            .build();

    public static final ScreenType SELL_OFFER_CONFIRMATION = new ScreenType.Builder()
            .name("SELL_OFFER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Sell Offer")
            .build();

    public static final ScreenType SELL_OFFER_OPTIONS = new ScreenType.Builder()
            .name("SELL_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("CANCEL_SELL_OFFER", BazaarSlots.ORDER_OPTIONS.CANCEL_SELL_OFFER::query)
            .build();

    public static final ScreenType INSTANT_SELL = new ScreenType.Builder()
            .name("INSTANT_SELL")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("SELL_INVENTORY", BazaarSlots.INSTANT_SELL_ITEM.SELL_INVENTORY::query)
            .build();

    public static final ScreenType INSTANT_SELL_ITEM_CONFIRMATION = new ScreenType.Builder()
            .name("INSTANT_SELL_ITEM_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm")
            .containerQuery("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_ITEM.CONFIRM_SELL::query)
            .build();

    public static final ScreenType INSTANT_SELL_GROUP_CONFIRMATION = new ScreenType.Builder()
            .name("INSTANT_SELL_GROUP_CONFIRMATION")
            .genericContainer()
            .containerTitle("Are you sure?")
            .containerQuery("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_GROUP.CONFIRM_SELL::query)
            .build();

    public static final Set<ScreenType> ALL = Set.of(
            MAIN_PAGE,
            SETTINGS_PAGE,
            ORDERS_PAGE,

            ITEM_PAGE,
            ITEMS_GROUP_PAGE,

            BUY_ORDER_AMOUNT,
            BUY_ORDER_PRICE,
            BUY_ORDER_CONFIRMATION,
            PENDING_BUY_ORDER_OPTIONS,
            COMPLETED_BUY_ORDER_OPTIONS,
            INSTANT_BUY,

            SELL_OFFER_AMOUNT,
            SELL_OFFER_PRICE,
            SELL_OFFER_CONFIRMATION,
            SELL_OFFER_OPTIONS,

            INSTANT_SELL,
            INSTANT_SELL_ITEM_CONFIRMATION,
            INSTANT_SELL_GROUP_CONFIRMATION
    );
}
