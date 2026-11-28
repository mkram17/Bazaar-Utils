package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.events.ProfileScopedEvent;
import kotlin.jvm.functions.Function2;
import tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class CurrentProfilePredicateProvider implements EventPredicateProvider {
    @Override
    public Function2<SkyBlockEvent, Object, Boolean> getPredicate(Method method) {
        if (!method.isAnnotationPresent(OnlyCurrentProfile.class)) return null;

        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || !ProfileScopedEvent.class.isAssignableFrom(params[0])) {
            String found = params.length == 0
                    ? "none"
                    : Arrays.stream(params).map(Class::getName).collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "@OnlyCurrentProfile on " + method.getDeclaringClass().getName() + "#" + method.getName()
                            + " requires a single ProfileScopedEvent parameter, found: " + found);
        }

        return (event, ctx) -> ((ProfileScopedEvent) event).getProfileKey().isCurrent();
    }
}
