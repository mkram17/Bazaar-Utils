package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

public final class UserOrdersStorage {
    public static final ProfileStorage<List<Order>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "user_orders",
            v -> Codec.list(Order.CODEC).xmap(ArrayList::new, ArrayList::new)
    );

    private UserOrdersStorage() {}
}