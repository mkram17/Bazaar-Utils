package com.github.mkram17.bazaarutils.config.hidden;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import java.util.ArrayList;
import java.util.List;

@Category(value = "general_config")
@ConfigOption.Hidden
public final class GeneralDataConfig {
    public static List<Order> userOrders = new ArrayList<>();
}
