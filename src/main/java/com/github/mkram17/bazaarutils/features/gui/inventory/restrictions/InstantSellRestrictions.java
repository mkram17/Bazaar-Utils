package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.components.InstantSellParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.SellablePageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;

//TODO maybe color chest if it is locked
@Module
public class InstantSellRestrictions extends RestrictionHelper<InstantSellRestrictions.InstantSellState> {
    public record InstantSellState(
            @NotNull
            ItemInfo targetItem,

            @NotNull
            List<RestrictionControl<?>> triggeredRestrictors
    ) implements RestrictionHelper.RestrictionState {}

    @Override
    public boolean isEnabled() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_TOGGLE && RestrictionTarget.isRestrictorFeatureEnabled(RestrictionTarget.INSTANT_SELL);
    }

    @Override
    protected int getClicksOverride() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_CLICKS_OVERRIDE;
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell protected by rules:";
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.RestrictionRules.restrictors(RestrictionTarget.INSTANT_SELL);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.ITEMS_GROUP_PAGE, BazaarScreenType.ITEM_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public InstantSellRestrictions() {
        super("Instant Sell Restrictions");
    }

    @Override
    protected Optional<InstantSellState> makeState(ContainerLoadedEvent event) {
        ScreenContext context = event.asContext();

        Optional<ItemInfo> instantSellItem = SellablePageLayout.getInstantSellItem(context);

        if (instantSellItem.isEmpty()) return Optional.empty();

        List<OrderInfo> orders = context.is(BazaarScreenType.ITEM_PAGE)
                ? InstantSellParser.parseItemPageOrder(instantSellItem.get().itemStack())
                        .map(InstantSellParser.InstantSellResult::items)
                        .orElse(List.of())
                : InstantSellParser.parseOrders(instantSellItem.get().itemStack())
                        .items();

        List<RestrictionControl<?>> triggered = getRestrictors().stream()
                .filter(control -> control.anyMatch(orders))
                .toList();

        return Optional.of(new InstantSellState(instantSellItem.get(), triggered));
    }
}