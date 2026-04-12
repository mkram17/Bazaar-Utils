package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.utils.bazaar.components.InstantSellParser;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellParser;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public InstantSellRestrictions() {
        super("Instant Sell Restrictions");
    }

    @Override
    public boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE, BazaarScreens.ITEM_PAGE, BazaarScreens.ITEMS_GROUP_PAGE);
    }

    @Override
    protected Optional<InstantSellState> makeState(ChestLoadedEvent event) {
        Optional<ScreenContext> context = ScreenManager.getInstance().current();
        if (context.isEmpty()) return Optional.empty();

        Optional<ItemInfo> instantSellItem = BazaarScreenHandler.getInstantSellItem(context.get());
        if (instantSellItem.isEmpty()) return Optional.empty();

        InstantSellParser.InstantSellResult result = SellParser.InstantSell.getResult();
        if (result == null) return Optional.empty();

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(result.items()))
                .toList());

        result.otherItems().ifPresent(other -> triggered.addAll(collectOtherItemsTriggered(other)));

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