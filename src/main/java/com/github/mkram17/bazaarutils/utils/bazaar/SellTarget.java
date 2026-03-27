package com.github.mkram17.bazaarutils.utils.bazaar;

/**
 * Represents the kind of bazaar sell relationship an item has on the current screen.
 */
public enum SellTarget {
    /**
     * This item is to be instantly sold on the current Bazaar Screen.
     */
    INSTANT_SELL,

    /**
     * This item is to be offered in the current sell offer carriage menu.
     */
    SELL_OFFER,
}