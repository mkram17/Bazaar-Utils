package com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.DoubleSellRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.SellRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictsell.controls.StringSellRestrictionControl;
import com.github.mkram17.bazaarutils.features.util.ConfigurableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.InstaSellUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import java.util.List;

//TODO maybe color chest if it is locked
@Module
public class InstantSellRestrictions extends BUListener implements ConfigurableFeature {
    private static final int INSTA_SELL_SLOT_INDEX = 47;

    public static boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_RESTRICTIONS_TOGGLE;
    }

    public InstantSellRestrictions() {}

    @Getter
    private transient int clicks = 0;

    private transient boolean isInstantSellRestricted;

    @Override
    protected void registerFabricEvents() {
        registerScreenEvent();
    }

    private void registerScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            clicks = 0;
            isInstantSellRestricted = true;
        });
    }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent e) {
        if (!isEnabled() || !ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE)) {
            return;
        }

        List<OrderInfo> items = InstaSellUtil.getInstaSellOrders(e.getItemStacks());
        isInstantSellRestricted = shouldRestrictInstantSell(items);
    }

    @EventHandler
    private void onClick(SlotClickEvent clickEvent){
        if (!isEnabled() || clickEvent.slot.getIndex() != INSTA_SELL_SLOT_INDEX) {
            return;
        }

        if (isInstantSellRestricted && clicks < InventoryConfig.INSTANT_SELL_RESTRICTIONS_CLICKS_OVERRIDE) {
            clicks++;
            PlayerActionUtil.notifyAll(getMessage());
            clickEvent.cancel();
        }
    }

    private boolean shouldRestrictInstantSell(List<OrderInfo> items){
        return items.stream().anyMatch(this::isItemRestricted);
    }

    public static void addRule(SellRestrictionControl<?> control){
//        TODO: deprecate this
//        controls.add(control);
    }

    private boolean isItemRestricted(OrderInfo item) {
        for (SellRestrictionControl<?> control : InventoryConfig.SellRestrictionsRules.restrictors()) {
            if (!control.isEnabled()) {
                continue;
            }

            if (control.shouldRestrict(item)) {
                return true;
            }
        }
        return false;
    }

    public String getMessage() {
        StringBuilder message = new StringBuilder("Sell protected by rules:");

        for (SellRestrictionControl<?> control : InventoryConfig.SellRestrictionsRules.restrictors()) {
            if (!control.isEnabled()) {
                continue;
            }

            if (control instanceof DoubleSellRestrictionControl doubleControl) {
                switch (doubleControl.getRule()) {
                    case NumericRestrictBy.PRICE -> message.append(" PRICE: ");
                    case NumericRestrictBy.VOLUME -> message.append(" VOLUME: ");
                }

                message.append(doubleControl.getAmount());
            } else {
                StringSellRestrictionControl stringControl = (StringSellRestrictionControl) control;

                message.append(" NAME: ");
                message.append(stringControl.getName());
            }
        }

        message.append(" (")
                .append("Safety Clicks Left: ")
                .append(InventoryConfig.INSTANT_SELL_RESTRICTIONS_CLICKS_OVERRIDE - clicks)
                .append(")");

        return message.toString();
    }
}
