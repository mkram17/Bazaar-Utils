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

    /**
     * Matcher over an explicit list of screens. An empty array yields a matcher that matches
     * nothing, mirroring the empty-input guard on {@link ScreenMatcher#except(Enum[])} —
     * {@link EnumSet#copyOf(java.util.Collection)} cannot infer the element type from an empty
     * collection and would otherwise throw {@code IllegalArgumentException: Collection is empty}.
     */
    public static ScreenMatcher<BazaarScreenType> of(BazaarScreenType[] types) {
        if (types.length == 0) return ScreenMatcher.of(BazaarScreenType.class, EnumSet.noneOf(BazaarScreenType.class));
        return ScreenMatcher.of(BazaarScreenType.class, EnumSet.copyOf(Arrays.asList(types)));
    }
}