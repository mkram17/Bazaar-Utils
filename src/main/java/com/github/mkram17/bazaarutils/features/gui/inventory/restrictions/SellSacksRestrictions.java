package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.StringRestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellSacksParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Module
public class SellSacksRestrictions extends RestrictionHelper<SellSacksRestrictions.SellSacksState> {
    public record SellSacksState(
            @NotNull
            ItemInfo restrictedItem,

            @NotNull
            List<OrderInfo> orders,

            @NotNull
            Optional<SellSacksParser.SellSacksResult.OtherItems> otherItems
    ) implements RestrictionHelper.RestrictionState {}

    @Override
    public boolean isEnabled() {
        return InventoryConfig.RESTRICTIONS_TOGGLE && RestrictionTarget.isRestrictorFeatureEnabled(RestrictionTarget.SELL_SACKS);
    }

    @Override
    protected int getClicksOverride() {
        return InventoryConfig.RESTRICTIONS_CLICKS_OVERRIDE;
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell sacks protected by rules:";
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.SellRestrictionsRules.restrictors(RestrictionTarget.SELL_SACKS);
    }


    public SellSacksRestrictions() {
        super("Sell Sacks Restrictions");
    }

    @Override
    public boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE, BazaarScreens.ITEM_PAGE, BazaarScreens.ITEMS_GROUP_PAGE);
    }

    @Override
    protected Optional<SellSacksState> makeState(ChestLoadedEvent event) {
        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        if (context.isEmpty()) return Optional.empty();

        Optional<ItemInfo> sellSacksItem = BazaarScreenHandler.getSellSacksItem(context.get());

        if (sellSacksItem.isEmpty()) return Optional.empty();

        SellSacksParser.SellSacksResult result = SellSacksParser.parseOrders(sellSacksItem.get().itemStack());

        return Optional.of(new SellSacksState(sellSacksItem.get(), result.items(), result.otherItems()));
    }

    @Override
    protected boolean computeRestriction(SellSacksState state) {
        return state.orders().stream().anyMatch(this::isItemRestricted)
                || state.otherItems().map(this::isOtherItemsRestricted).orElse(false);
    }

    private boolean isOtherItemsRestricted(SellSacksParser.SellSacksResult.OtherItems otherItems) {
        for (RestrictionControl<?> control : getRestrictors()) {
            if (!control.isEnabled()) continue;
            if (!(control instanceof DoubleRestrictionControl doubleControl)) continue;

            boolean restricted = switch (doubleControl.getRule()) {
                case VOLUME -> otherItems.volume() > doubleControl.getAmount();
                case PRICE -> otherItems.totalValue() > doubleControl.getAmount();
            };

            if (restricted) return true;
        }

        return false;
    }
}