package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ItemModifiers;
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

    @Subscription(priority = Subscription.HIGH)
    @OnlyOnSkyBlock
    private void onChestLoaded(ChestLoadedEvent event) {
        helpers().forEach(helper -> {
            ItemModifiers.registerDynamic(helper);
            helper.onChestLoaded(event);
        });
    }

    @Subscription
    private void onContainerClose(ContainerCloseEvent event) {
        helpers().forEach(ItemModifiers::unregisterDynamic);
    }
}
