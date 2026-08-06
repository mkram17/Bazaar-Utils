package com.github.mkram17.bazaarutils.features.gui.buttons.inputhelper;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SignInputHelper;

import java.util.List;

@Module
public class PriceHelpers extends InputHelperDispatcher {
    @Override
    protected List<SignInputHelper.TransactionCost> helpers() {
        return ButtonsConfig.HelpersConfig.priceHelpers();
    }
}
