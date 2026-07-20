package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.events.RegistrationScope;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.EventPredicateProvider;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.lang.reflect.Method;

public class BazaarScreenEventPredicateProvider implements EventPredicateProvider {

    @Override
    public Function2<SkyBlockEvent, Object, Boolean> getPredicate(Method method) {
        if (!method.isAnnotationPresent(OnlyBazaarScreen.class)) return null;

        OnlyBazaarScreen annotation = method.getAnnotation(OnlyBazaarScreen.class);

        if (annotation.useConstraintsInterface()) {
            ScreenConstrained constrained = resolveConstrained(method);
            ScreenMatcher<BazaarScreenType> exclusions = annotation.except().length > 0
                    ? BazaarScreenMatcher.any().except(annotation.except())
                    : null;
            return (event, ctx) -> {
                ScreenContext context = resolveContext(event);
                if (exclusions != null && !exclusions.matches(context)) return false;
                return constrained.appliesToScreen(context);
            };
        }

        ScreenMatcher<BazaarScreenType> matcher = annotation.any()
                ? BazaarScreenMatcher.any()
                : BazaarScreenMatcher.of(annotation.value());

        if (annotation.except().length > 0) matcher = matcher.except(annotation.except());

        ScreenMatcher<BazaarScreenType> finalMatcher = matcher;

        return (event, ctx) -> finalMatcher.matches(resolveContext(event));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static @Nullable ScreenContext resolveContext(SkyBlockEvent event) {
        if (event instanceof ContainerLoadedEvent cle) return cle.asContext();

        return ScreenManager.getInstance().currentOrNull();
    }

    private static ScreenConstrained resolveConstrained(Method method) {
        Object instance = RegistrationScope.current()
                .map(RegistrationScope::getInstance)
                .orElseThrow(() -> new IllegalStateException(
                        "@OnlyBazaarScreen(useConstraintsInterface) built outside registration context on "
                                + qualifiedName(method)));

        if (!(instance instanceof ScreenConstrained sc)) {
            throw new IllegalStateException(
                    "@OnlyBazaarScreen on " + qualifiedName(method)
                            + ": useConstraintsInterface=true but instance does not implement ScreenConstrained.");
        }

        return sc;
    }

    private static String qualifiedName(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}