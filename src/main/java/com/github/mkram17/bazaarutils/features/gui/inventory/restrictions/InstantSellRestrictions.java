package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.SellableAPI;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
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

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public InstantSellRestrictions() {
        super("Instant Sell Restrictions");
    }

    @Override
    protected Optional<InstantSellState> makeState(ContainerLoadedEvent event) {
        if (!SellableAPI.InstantSell.hasResult()) return Optional.empty();

        ScreenContext context = event.asContext();

        Optional<ItemInfo> instantSellItem = SellablePageLayout.getInstantSellItem(context);

        if (instantSellItem.isEmpty()) return Optional.empty();

        List<OrderInfo> items = SellableAPI.InstantSell.orders();
        Optional<InstantSellParser.InstantSellResult.OtherItems> otherItems = SellableAPI.InstantSell.otherItems();

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(items))
                .toList());

        otherItems.ifPresent(other -> triggered.addAll(collectOtherItemsTriggered(other)));

        return Optional.of(new InstantSellState(instantSellItem.get(), List.copyOf(triggered)));
    }

    private List<RestrictionControl<?>> collectOtherItemsTriggered(InstantSellParser.InstantSellResult.OtherItems otherItems) {
        return getRestrictors().stream()
                .filter(control -> control instanceof DoubleRestrictionControl doubleControl && switch (doubleControl.getRule()) {
                    case PRICE -> otherItems.totalValue() > doubleControl.getAmount();
                    case VOLUME -> otherItems.volume() > doubleControl.getAmount();
                })
                .toList();
    }
}