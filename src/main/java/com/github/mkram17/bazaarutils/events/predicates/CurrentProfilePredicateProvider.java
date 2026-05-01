package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.events.ProfileScopedEvent;
import kotlin.jvm.functions.Function2;
import tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.lang.reflect.Method;

public class CurrentProfilePredicateProvider implements EventPredicateProvider {
    @Override
    public Function2<SkyBlockEvent, Object, Boolean> getPredicate(Method method) {
        if (!method.isAnnotationPresent(OnlyCurrentProfile.class)) return null;

        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || !ProfileScopedEvent.class.isAssignableFrom(params[0])) {
            throw new IllegalStateException(
                    "@OnlyCurrentProfile on " + method.getDeclaringClass().getName() + "#" + method.getName()
                            + " requires a ProfileScopedEvent parameter, found "
                            + (params.length == 1 ? params[0].getName() : "none"));
        }

        return (event, ctx) -> ((ProfileScopedEvent) event).getProfileKey().isCurrent();
    }
}