package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import org.jetbrains.annotations.Nullable;

public interface ScreenConstrained {

    ScreenMatcher<BazaarScreenType> screenConstraints();

    default BazaarScreenType[] getTargetScreens() {
        return new BazaarScreenType[0];
    }

    default boolean appliesToScreen(@Nullable ScreenContext context) {
        if (context == null) return false;

        BazaarScreenType[] userScreens = getTargetScreens();

        if (userScreens.length > 0) {
            for (BazaarScreenType t : userScreens) {
                if (context.is(t)) return true;
            }

            return false;
        }

        return screenConstraints().matches(context);
    }

    default boolean inCorrectScreen(ContainerLoadedEvent event) {
        return appliesToScreen(event.asContext());
    }

    default boolean inCorrectScreen() {
        return appliesToScreen(ScreenManager.getInstance().currentOrNull());
    }
}
