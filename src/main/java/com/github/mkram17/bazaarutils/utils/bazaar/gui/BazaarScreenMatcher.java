package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;

import java.util.Arrays;
import java.util.EnumSet;


public final class BazaarScreenMatcher {
    private BazaarScreenMatcher() {}

    public static ScreenMatcher<BazaarScreenType> any() {
        return ScreenMatcher.any(BazaarScreenType.class);
    }

    public static ScreenMatcher<BazaarScreenType> of(BazaarScreenType first, BazaarScreenType... rest) {
        return ScreenMatcher.of(BazaarScreenType.class, EnumSet.of(first, rest));
    }

    public static ScreenMatcher<BazaarScreenType> of(BazaarScreenType[] types) {
        return ScreenMatcher.of(BazaarScreenType.class, EnumSet.copyOf(Arrays.asList(types)));
    }
}