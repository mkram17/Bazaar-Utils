package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.bazaar.RestrictionHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellableLore;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Safety gate for the one-click bulk sells — Instant Sell and Sell Sacks. Both read the same lore,
 * live on the same four screens, and answer to the same rules, so the only thing a subclass says
 * is which button it guards and where {@link com.github.mkram17.bazaarutils.data.SellableAPI}
 * keeps that button's parse.
 */
public abstract class SellableRestrictions extends RestrictionHelper<SellableRestrictions.SellableState> {
    public record SellableState(
            @NotNull
            ItemInfo targetItem,

            @NotNull
            List<RestrictionControl<?>> triggeredRestrictors
    ) implements RestrictionHelper.RestrictionState {}

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(
            BazaarScreenType.MAIN_PAGE,
            BazaarScreenType.SEARCH_PAGE,
            BazaarScreenType.PRODUCTS_CATALOG_PAGE,
            BazaarScreenType.PRODUCT_PAGE
    );

    private final RestrictionTarget target;

    protected SellableRestrictions(String name, RestrictionTarget target) {
        super(name);

        this.target = target;
    }

    /** Whether the button this guards has been parsed on the current screen. */
    protected abstract boolean hasSellableData();

    /** The products the button would sell, one entry each. */
    protected abstract List<OrderInfo> sellableOrders();

    /** The tail the button folds into a single "Other items" line, when it has one. */
    protected abstract Optional<SellableLore.OtherItems> foldedItems();

    /** The button itself, whichever slot this screen puts it in. */
    protected abstract Optional<ItemInfo> targetItem(ScreenContext context);

    @Override
    public boolean isEnabled() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_TOGGLE && RestrictionTarget.isRestrictorFeatureEnabled(target);
    }

    @Override
    protected int getClicksOverride() {
        return InventoryConfig.RestrictionRules.RESTRICTIONS_CLICKS_OVERRIDE;
    }

    @Override
    protected List<RestrictionControl<?>> getRestrictors() {
        return InventoryConfig.RestrictionRules.restrictors(target);
    }

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    @Override
    protected Optional<SellableState> makeState(ContainerLoadedEvent event) {
        if (!hasSellableData()) return Optional.empty();

        Optional<ItemInfo> targetItem = targetItem(event.asContext());

        if (targetItem.isEmpty()) return Optional.empty();

        List<OrderInfo> items = sellableOrders();

        Set<RestrictionControl<?>> triggered = new LinkedHashSet<>(getRestrictors().stream()
                .filter(control -> control.anyMatch(items))
                .toList());

        foldedItems().ifPresent(folded -> triggered.addAll(collectFoldedTriggered(folded)));

        return Optional.of(new SellableState(targetItem.get(), List.copyOf(triggered)));
    }

    /**
     * The folded tail has no per-product breakdown, so only the rules expressed as a total —
     * price and volume — can be checked against it.
     */
    private List<RestrictionControl<?>> collectFoldedTriggered(SellableLore.OtherItems folded) {
        return getRestrictors().stream()
                .filter(control -> control instanceof DoubleRestrictionControl doubleControl && switch (doubleControl.getRule()) {
                    case PRICE -> folded.totalValue() > doubleControl.getAmount();
                    case VOLUME -> folded.volume() > doubleControl.getAmount();
                })
                .toList();
    }
}
