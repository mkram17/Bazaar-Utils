package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.minecraft.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

import java.util.List;

@Module
public class AmountHelpers extends BUListener {
    private static List<SignInputHelper.TransactionAmount> helpers() {
        return ButtonsConfig.HelpersConfig.amountHelpers();
    }

    public AmountHelpers() {
        super();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        helpers().forEach(helper -> helper.onContainerLoaded(event));
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
