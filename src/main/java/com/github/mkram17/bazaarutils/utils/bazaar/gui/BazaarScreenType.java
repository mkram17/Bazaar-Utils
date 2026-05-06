package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import net.minecraft.client.gui.screens.Screen;

public enum BazaarScreenType implements ScreenType {

    MAIN_PAGE(
            ScreenType.named("MAIN_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Bazaar ➜ "))
    ),

    SEARCH_ITEM_INPUT(
            ScreenType.named("SEARCH_ITEM_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(3, "Enter query"))
    ),

    SEARCH_PAGE(
            ScreenType.named("SEARCH_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Bazaar ➜ \""))
    ),

    SETTINGS_PAGE(
            ScreenType.named("SETTINGS_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Bazaar ➜ Settings"))
    ),

    ORDERS_PAGE(
            ScreenType.named("ORDERS_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Bazaar Orders"))
    ),

    ITEM_PAGE(
            ScreenType.named("ITEM_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle(" ➜ "))
                    .and(ScreenType.hasSlot("VIEW_GRAPHS", BazaarSlots.ITEM_PAGE.VIEW_GRAPHS::query))
    ),

    GRAPHS_PAGE(
            ScreenType.named("GRAPHS_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle(" ➜ Grap"))
                    .and(ScreenType.hasSlot("INSTANT_SELL_MOVING_COINS_REPORT", BazaarSlots.GRAPHS_PAGE.INSTANT_SELL_MOVING_COINS_REPORT::query))
    ),

    ITEMS_GROUP_PAGE(
            ScreenType.named("ITEMS_GROUP_PAGE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle(" ➜ "))
                    .and(ScreenType.hasSlot("SWITCH_VIEW_MODE", BazaarSlots.ITEMS_GROUP_PAGE.SWITCH_VIEW_MODE::query))
    ),

    BUY_ORDER_AMOUNT(
            ScreenType.named("BUY_ORDER_AMOUNT", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("How many do you want?"))
    ),

    BUY_ORDER_CUSTOM_AMOUNT_INPUT(
            ScreenType.named("BUY_ORDER_CUSTOM_AMOUNT_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(2, "Enter amount"))
                    .and(ScreenType.hasSignLine(3, "to order"))
                    .and(ScreenType.hasPreviousScreen(BUY_ORDER_AMOUNT))
    ),

    BUY_ORDER_PRICE(
            ScreenType.named("BUY_ORDER_PRICE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("How much do you want to pay?"))
    ),

    BUY_ORDER_CUSTOM_PRICE_INPUT(
            ScreenType.named("BUY_ORDER_CUSTOM_PRICE_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(2, "Enter price"))
                    .and(ScreenType.hasSignLine(3, "big nerd"))
    ),

    BUY_ORDER_CONFIRMATION(
            ScreenType.named("BUY_ORDER_CONFIRMATION", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Confirm Buy Order"))
    ),

    PENDING_BUY_ORDER_OPTIONS(
            ScreenType.named("PENDING_BUY_ORDER_OPTIONS", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Order options"))
                    .and(ScreenType.hasSlot("FLIP_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_UNFILLED_BUY_ORDER::query))
                    .and(ScreenType.hasSlot("CANCEL_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_UNFILLED_BUY_ORDER::query))
    ),

    COMPLETED_BUY_ORDER_OPTIONS(
            ScreenType.named("COMPLETED_BUY_ORDER_OPTIONS", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Order options"))
                    .and(ScreenType.hasSlot("FLIP_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_FILLED_BUY_ORDER::query))
                    .and(ScreenType.hasSlot("CANCEL_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_FILLED_BUY_ORDER::query))
    ),

    COMPLETED_BUY_ORDER_FLIP_PRICE_INPUT(
            ScreenType.named("COMPLETED_BUY_ORDER_FLIP_PRICE_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(1, "^^Flipping^^"))
                    .and(ScreenType.hasSignLine(2, "Previous price:"))
    ),

    INSTANT_BUY(
            ScreenType.named("INSTANT_BUY", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("➜ Inst"))
                    .and(ScreenType.hasSlot("INPUT_CUSTOM_AMOUNT", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT::query))
    ),

    INSTANT_BUY_CUSTOM_AMOUNT_INPUT(
            ScreenType.named("INSTANT_BUY_CUSTOM_AMOUNT_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(2, "Enter amount"))
                    .and(ScreenType.hasSignLine(3, "to order"))
                    .and(ScreenType.hasPreviousScreen(INSTANT_BUY))
    ),

    SELL_OFFER_AMOUNT(
            ScreenType.named("SELL_OFFER_AMOUNT", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("How many are you selling?"))
    ),

    SELL_OFFER_CUSTOM_AMOUNT_INPUT(
            ScreenType.named("SELL_OFFER_CUSTOM_AMOUNT_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(2, "Enter amount"))
                    .and(ScreenType.hasSignLine(3, "to sell"))
    ),

    SELL_OFFER_PRICE(
            ScreenType.named("SELL_OFFER_PRICE", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("At what price are you selling?"))
    ),

    SELL_OFFER_CUSTOM_PRICE_INPUT(
            ScreenType.named("SELL_OFFER_CUSTOM_PRICE_INPUT", ScreenType.isSign())
                    .and(ScreenType.hasSignLine(2, "Enter price"))
                    .and(ScreenType.hasSignLine(3, "per unit"))
    ),

    SELL_OFFER_CONFIRMATION(
            ScreenType.named("SELL_OFFER_CONFIRMATION", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Confirm Sell Offer"))
    ),

    SELL_OFFER_OPTIONS(
            ScreenType.named("SELL_OFFER_OPTIONS", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Order options"))
                    .and(ScreenType.hasSlot("CANCEL_SELL_OFFER", BazaarSlots.ORDER_OPTIONS.CANCEL_SELL_OFFER::query))
    ),

    INSTANT_SELL(
            ScreenType.named("INSTANT_SELL", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("➜ Inst"))
                    .and(ScreenType.hasSlot("SELL_INVENTORY", BazaarSlots.INSTANT_SELL_ITEM.SELL_INVENTORY::query))
    ),

    INSTANT_SELL_ITEM_CONFIRMATION(
            ScreenType.named("INSTANT_SELL_ITEM_CONFIRMATION", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Confirm"))
                    .and(ScreenType.hasSlot("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_ITEM.CONFIRM_SELL::query))
    ),

    INSTANT_SELL_GROUP_CONFIRMATION(
            ScreenType.named("INSTANT_SELL_GROUP_CONFIRMATION", ScreenType.isContainer())
                    .and(ScreenType.hasTitle("Are you sure?"))
                    .and(ScreenType.hasSlot("CONFIRM_SELL", BazaarSlots.INSTANT_SELL_GROUP.CONFIRM_SELL::query))
    );

    private final ScreenType delegate;

    BazaarScreenType(ScreenType delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean test(Screen screen) {
        return delegate.test(screen);
    }

    public static void registerAll() {
        for (BazaarScreenType type : values()) {
            ScreenManager.register(type);
        }
    }
}