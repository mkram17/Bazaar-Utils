package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.data.SellableAPI;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellableLore;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.SellablePageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;

import java.util.List;
import java.util.Optional;

//TODO maybe color chest if it is locked
@Module
public class InstantSellRestrictions extends SellableRestrictions {
    public InstantSellRestrictions() {
        super("Instant Sell Restrictions", RestrictionTarget.INSTANT_SELL);
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell protected by rules:";
    }

    @Override
    protected boolean hasSellableData() {
        return SellableAPI.InstantSell.hasResult();
    }

    @Override
    protected List<OrderInfo> sellableOrders() {
        return SellableAPI.InstantSell.orders();
    }

    @Override
    protected Optional<SellableLore.OtherItems> foldedItems() {
        return SellableAPI.InstantSell.otherItems();
    }

    @Override
    protected Optional<ItemInfo> targetItem(ScreenContext context) {
        return SellablePageLayout.getInstantSellItem(context);
    }
}
