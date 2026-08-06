package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.data.SellableAPI;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellSacksParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.SellablePageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ItemModifier
public class SellSacksRestrictions extends RestrictionHelper<SellSacksRestrictions.SellSacksState> {
    public record SellSacksState(
            @NotNull
            ItemInfo targetItem,

            @NotNull
            List<RestrictionControl<?>> triggeredRestrictors
    ) implements RestrictionHelper.RestrictionState {}

    @Override
    public boolean isEnabled() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_TOGGLE && RestrictionTarget.isRestrictorFeatureEnabled(RestrictionTarget.SELL_SACKS);
    }

    @Override
    protected int getClicksOverride() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_CLICKS_OVERRIDE;
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.RestrictionRules.restrictors(RestrictionTarget.SELL_SACKS);
    }

    public SellSacksRestrictions() {
        super("Sell Sacks Restrictions");
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    public final EnumSet<AbstractItemModifier.ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER);

    @Override
    public EnumSet<AbstractItemModifier.ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES; // to prevent instantiating the LIST every single iteration
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell sacks protected by rules:";
    }

    @Override
    protected Optional<SellSacksState> makeState(ContainerLoadedEvent event) {
        if (!SellableAPI.SellSacks.hasResult()) return Optional.empty();

        ScreenContext context = event.asContext();

        Optional<ItemInfo> sellSacksItem = SellablePageLayout.getSellSacksItem(context);

        if (sellSacksItem.isEmpty()) return Optional.empty();

        List<OrderInfo> items = SellableAPI.SellSacks.orders();
        Optional<SellSacksParser.SellSacksResult.OtherItems> otherItems = SellableAPI.SellSacks.otherItems();

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(items))
                .toList());

        otherItems.ifPresent(other -> triggered.addAll(collectOtherItemsTriggered(other)));

        return Optional.of(new SellSacksState(sellSacksItem.get(), List.copyOf(triggered)));
    }

    private List<RestrictionControl<?>> collectOtherItemsTriggered(SellSacksParser.SellSacksResult.OtherItems otherItems) {
        return getRestrictors().stream()
                .filter(control -> control instanceof DoubleRestrictionControl doubleControl && switch (doubleControl.getRule()) {
                    case PRICE -> otherItems.totalValue() > doubleControl.getAmount();
                    case VOLUME -> otherItems.volume() > doubleControl.getAmount();
                })
                .toList();
    }
}