package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

public final class UserOrdersStorage {
    /**
     * Orders are event bus listeners, so the storage owns their subscription: they go on the bus when a
     * profile's orders are loaded and come off it before that profile's orders are discarded. Decoding
     * an Order does not subscribe it.
     */
    public static final ProfileStorage<List<Order>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "user_orders",
            v -> Codec.list(Order.CODEC).xmap(ArrayList::new, ArrayList::new),
            OrderUtil::attachAll,
            OrderUtil::detachAll
    );

    private UserOrdersStorage() {}
}