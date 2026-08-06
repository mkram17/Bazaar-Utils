package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.features.ItemModifiers;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;

import java.util.List;

@Module
public class PriceHelpers extends BUListener {
    private static List<SignInputHelper.TransactionCost> helpers() {
        return ButtonsConfig.HelpersConfig.priceHelpers();
    }

    public PriceHelpers() {
        super();
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    private void onContainerLoaded(ContainerLoadedEvent event) {
        helpers().forEach(helper -> {
            ItemModifiers.registerDynamic(helper);
            helper.onContainerLoaded(event);
        });
    }

    @Subscription
    private void onContainerClose(ContainerCloseEvent event) {
        helpers().forEach(ItemModifiers::unregisterDynamic);
    }
}
