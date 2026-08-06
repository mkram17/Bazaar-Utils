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

@Module
public class SellSacksRestrictions extends SellableRestrictions {
    public SellSacksRestrictions() {
        super("Sell Sacks Restrictions", RestrictionTarget.SELL_SACKS);
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell sacks protected by rules:";
    }

    @Override
    protected boolean hasSellableData() {
        return SellableAPI.SellSacks.hasResult();
    }

    @Override
    protected List<OrderInfo> sellableOrders() {
        return SellableAPI.SellSacks.orders();
    }

    @Override
    protected Optional<SellableLore.OtherItems> foldedItems() {
        return SellableAPI.SellSacks.otherItems();
    }

    @Override
    protected Optional<ItemInfo> targetItem(ScreenContext context) {
        return SellablePageLayout.getSellSacksItem(context);
    }
}
