package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.util.List;

@Module
public class AmountHelpers extends BUListener {
    private static List<SignInputHelper.TransactionAmount> helpers() {
        return ButtonsConfig.HelpersConfig.amountHelpers();
    }

    public AmountHelpers() {
        super();
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    private void onContainerLoaded(ContainerLoadedEvent event) {
        helpers().forEach(helper -> helper.onContainerLoaded(event));
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onReplaceItem(ReplaceItemEvent event) {
        helpers().forEach(helper -> helper.onReplaceItem(event));
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onSlotClicked(SlotClickEvent event) {
        helpers().forEach(helper -> helper.onSlotClicked(event));
    }
}
