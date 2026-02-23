package com.github.mkram17.bazaarutils.config.features;

import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.FlipHelper;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.MaxBuyOrder;
import com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper.customorder.CustomOrder;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

public class FeatureConfig {
    @ConfigEntry(id = "customOrders")
    public final List<CustomOrder> customOrders = new ArrayList<>();

    @ConfigEntry(id = "flipHelper")
    public FlipHelper flipHelper = new FlipHelper(true, PricingPosition.COMPETITIVE, 17);

    @ConfigEntry(id = "maxBuyOrder")
    public MaxBuyOrder maxBuyOrder = new MaxBuyOrder(true);
}
