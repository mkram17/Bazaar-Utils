package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class UserOrdersStorage {
    public static final DataStorage<List<Order>> INSTANCE = new DataStorage<>(
            0,
            ArrayList::new,
            "user_orders",
            v -> Codec.list(Order.CODEC).xmap(ArrayList::new, ArrayList::new)
    );

    private UserOrdersStorage() {}
}