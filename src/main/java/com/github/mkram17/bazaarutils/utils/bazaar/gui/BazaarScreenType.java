package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.UnaryOperator;

public enum BazaarScreenType implements ScreenType {
    MAIN_PAGE(query -> query
            .name("MAIN_PAGE")
            .genericContainer()
            .containerTitle("Bazaar ➜ ")
    ),

    SETTINGS_PAGE(query -> query
            .name("SETTINGS_PAGE")
            .genericContainer()
            .containerTitle("Settings")
    ),

    ORDERS_PAGE(query -> query
            .name("ORDERS_PAGE")
            .genericContainer()
            .containerTitle("Bazaar Orders")
    ),

    ITEM_PAGE(query -> query
            .name("ITEM_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("VIEW_GRAPHS", BazaarSlots.ITEM_PAGE.VIEW_GRAPHS::query)
    ),

    ITEMS_GROUP_PAGE(query -> query
            .name("ITEMS_GROUP_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("SWITCH_VIEW_MODE", BazaarSlots.ITEMS_GROUP_PAGE.SWITCH_VIEW_MODE::query)
    ),

    BUY_ORDER_AMOUNT(query -> query
            .name("BUY_ORDER_AMOUNT")
            .genericContainer()
            .containerTitle("How many do you want?")
    ),

    BUY_ORDER_PRICE(query -> query
            .name("BUY_ORDER_PRICE")
            .genericContainer()
            .containerTitle("How much do you want to pay?")
    ),

    BUY_ORDER_CONFIRMATION(query -> query
            .name("BUY_ORDER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Buy Order")
    ),

    PENDING_BUY_ORDER_OPTIONS(query -> query
            .name("PENDING_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_UNFILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_UNFILLED_BUY_ORDER::query)
    ),

    COMPLETED_BUY_ORDER_OPTIONS(query -> query
            .name("COMPLETED_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_FILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_FILLED_BUY_ORDER::query)
    ),

    INSTANT_BUY(query -> query
            .name("INSTANT_BUY")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("INPUT_CUSTOM_AMOUNT", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT::query)
    ),

    SELL_OFFER_AMOUNT(query -> query
            .name("SELL_OFFER_AMOUNT")
            .genericContainer()
            .containerTitle("How many are you selling?")
    ),

    SELL_OFFER_PRICE(query -> query
            .name("SELL_OFFER_PRICE")
            .genericContainer()
            .containerTitle("At what price are you selling?")
    ),

    SELL_OFFER_CONFIRMATION(query -> query
            .name("SELL_OFFER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Sell Offer")
    ),

    SELL_OFFER_OPTIONS(query -> query
            .name("SELL_OFFER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("CANCEL_SELL_OFFER", BazaarSlots.ORDER_OPTIONS.CANCEL_SELL_OFFER::query)
    ),

    INSTANT_SELL(query -> query
            .name("INSTANT_SELL")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("SELL_INVENTORY", BazaarSlots.INSTANT_SELL_ITEM.SELL_INVENTORY::query)
    ),

    INSTANT_SELL_ITEM_CONFIRMATION(query -> query
            .name("INSTANT_SELL_ITEM_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm")
            .containerQuery("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_ITEM.CONFIRM_SELL::query)
    ),

    INSTANT_SELL_GROUP_CONFIRMATION(query -> query
            .name("INSTANT_SELL_GROUP_CONFIRMATION")
            .genericContainer()
            .containerTitle("Are you sure?")
            .containerQuery("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_GROUP.CONFIRM_SELL::query)
    );

    private final ScreenType delegate;

    BazaarScreenType(UnaryOperator<Builder> consumer) {
        this.delegate = consumer.apply(new ScreenType.Builder().name(name())).build();
    }

    @Override
    public boolean test(Screen screen) {
        return delegate.test(screen);
    }

    @Override
    public String asString() {
        return delegate.asString();
    }

    @Override
    public String shortName() {
        return delegate.shortName();
    }

    public static void registerAll() {
        for (BazaarScreenType type : values()) {
            ScreenManager.register(type);
        }
    }
}