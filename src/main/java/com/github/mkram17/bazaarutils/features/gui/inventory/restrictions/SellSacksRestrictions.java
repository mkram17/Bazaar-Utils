package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellSacksParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.SellablePageLayout;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Module
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
    protected String getMessagePrefix() {
        return "Sell sacks protected by rules:";
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.RestrictionRules.restrictors(RestrictionTarget.SELL_SACKS);
    }


    public SellSacksRestrictions() {
        super("Sell Sacks Restrictions");
    }

    @Override
    public boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreenType.MAIN_PAGE, BazaarScreenType.ITEM_PAGE, BazaarScreenType.ITEMS_GROUP_PAGE);
    }

    @Override
    protected Optional<SellSacksState> makeState(ContainerLoadedEvent event) {
        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        if (context.isEmpty()) return Optional.empty();

        Optional<ItemInfo> sellSacksItem = SellablePageLayout.getSellSacksItem(context.get());

        if (sellSacksItem.isEmpty()) return Optional.empty();

        SellSacksParser.SellSacksResult result = SellSacksParser.parseOrders(sellSacksItem.get().itemStack());

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(result.items()))
                .toList());

        result.otherItems().ifPresent(other -> triggered.addAll(collectOtherItemsTriggered(other)));

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