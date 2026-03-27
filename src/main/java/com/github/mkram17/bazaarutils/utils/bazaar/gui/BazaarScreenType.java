package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;

public enum BazaarScreenType {
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
    SELL_ORDER_AMOUNT,
    SELL_ORDER_PRICE,
    SELL_ORDER_CONFIRMATION,
    SELL_ORDER_OPTIONS,
    INSTANT_SELL;

    public ScreenType get() {
        return BazaarScreens.ALL_MAP.get(this);
    }
}