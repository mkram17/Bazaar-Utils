package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

/**
 * Lifecycle status for a tracked bazaar order.
 */
public enum OrderStatus {
    /** Order is currently active/open on the bazaar. */
    SET,

    /** Order has completed and is no longer active. */
    FILLED
}
