package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class UserOrdersStorage {
    private static final Type TYPE = new TypeToken<List<Order>>(){}.getType();

    public static final DataStorage<List<Order>> INSTANCE = new DataStorage<>(ArrayList::new, "user_orders", TYPE);

    private UserOrdersStorage() {}
}