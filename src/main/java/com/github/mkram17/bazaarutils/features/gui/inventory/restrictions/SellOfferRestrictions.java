package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.TransactionAPI;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ItemModifier
public class SellOfferRestrictions extends RestrictionHelper<SellOfferRestrictions.SellOfferState> {
    public record SellOfferState(
            @NotNull
            ItemInfo targetItem,

            @NotNull
            List<RestrictionControl<?>> triggeredRestrictors
    ) implements RestrictionHelper.RestrictionState {}

    @Override
    public boolean isEnabled() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_TOGGLE && RestrictionTarget.isRestrictorFeatureEnabled(RestrictionTarget.SELL_OFFER);
    }

    @Override
    protected int getClicksOverride() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_CLICKS_OVERRIDE;
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.RestrictionRules.restrictors(RestrictionTarget.SELL_OFFER);
    }

    public SellOfferRestrictions() {
        super("Sell Offer Restrictions");
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.SELL_OFFER_CONFIRMATION);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public final EnumSet<AbstractItemModifier.ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER);

    @Override
    public EnumSet<AbstractItemModifier.ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES; // to prevent instantiating the LIST every single iteration
    }

    @Override
    protected String getMessagePrefix() {
        return "Sell offer protected by rules:";
    }

    @Override
    protected Optional<SellOfferState> makeState(ContainerLoadedEvent event) {
        var projected = TransactionAPI.get();

        if (projected.isEmpty()) {
            LOG.warn("SellOfferRestrictions.makeState: TransactionAPI.get result is empty - data not yet parsed");

            return Optional.empty();
        }

        ScreenContext context = event.asContext();

        Optional<ItemInfo> sellOfferItem = TransactionPageLayout.getConfirmSellOfferItem(context);
        if (sellOfferItem.isEmpty()) {
            LOG.warn("SellOfferRestrictions.makeState: no sell offer item in layout for screen '{}'", context);

            return Optional.empty();
        }

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(List.of(projected.get())))
                .toList());

        return Optional.of(new SellOfferState(sellOfferItem.get(), List.copyOf(triggered)));
    }
}