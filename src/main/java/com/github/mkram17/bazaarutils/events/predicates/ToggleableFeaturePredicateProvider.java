package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import kotlin.jvm.functions.Function2;
import tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.lang.reflect.Method;

public class ToggleableFeaturePredicateProvider implements EventPredicateProvider {
    @Override
    public Function2<SkyBlockEvent, Object, Boolean> getPredicate(Method method) {
        if (!method.isAnnotationPresent(OnlyWhenEnabled.class)) return null;

        Object instance = BUListener.currentRegistration().orElseThrow(() -> new IllegalStateException("@OnlyWhenEnabled predicate built outside of registration context on " + method.getDeclaringClass().getName() + "#" + method.getName()));

        if (!(instance instanceof ToggleableFeature feature)) throw new IllegalStateException("@OnlyWhenEnabled on " + method.getDeclaringClass().getName() + "#" + method.getName() + " but instance does not implement ToggleableFeature");

        return (event, context) -> feature.isEnabled();
    }
}