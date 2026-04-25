package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

import java.util.List;

@Module
public class PriceHelpers extends BUListener {
    private static List<SignInputHelper.TransactionCost> helpers() {
        return ButtonsConfig.HelpersConfig.priceHelpers();
    }

    public PriceHelpers() {
        super();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onChestLoaded(ContainerLoadedEvent event) {
        helpers().forEach(helper -> helper.onChestLoaded(event));
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onReplaceItem(ReplaceItemEvent event) {
        helpers().forEach(helper -> helper.onReplaceItem(event));
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onSlotClicked(SlotClickEvent event) {
        helpers().forEach(helper -> helper.onSlotClicked(event));
    }
}
