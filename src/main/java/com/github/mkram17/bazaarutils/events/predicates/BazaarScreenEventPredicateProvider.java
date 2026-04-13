package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import kotlin.jvm.functions.Function2;
import tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BazaarScreenEventPredicateProvider implements EventPredicateProvider {

    @Override
    public Function2<SkyBlockEvent, Object, Boolean> getPredicate(Method method) {
        OnlyBazaarScreen annotation = method.getAnnotation(OnlyBazaarScreen.class);
        if (annotation == null) return null;

        Set<ScreenType> wanted = Arrays.stream(annotation.value()).collect(Collectors.toSet());

        return (event, context) -> {
            Optional<ScreenType> current = ScreenManager.getInstance().current().flatMap(ScreenContext::type);

            return current.map(wanted::contains).orElse(false);
        };
    }
}