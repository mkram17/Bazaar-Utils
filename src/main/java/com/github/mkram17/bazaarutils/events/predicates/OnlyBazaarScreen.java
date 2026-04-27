package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnlyBazaarScreen {

    /**
     * Explicit screen whitelist.
     * Mutually exclusive with {@code anyBazaar = true}.
     */
    BazaarScreenType[] value() default {};

    /**
     * Screens to exclude from matching.
     * Applies to all three modes (explicit, anyBazaar, and delegated).
     */
    BazaarScreenType[] except() default {};

    /** Match any {@link BazaarScreenType}. Mutually exclusive with {@code value()}. */
    boolean any() default false;

    /**
     * Delegate the screen list to the registering instance's
     * {@link ScreenConstrained#appliesToScreen}.
     * Mutually exclusive with {@code value()} and {@code any}.
     */
    boolean useConstrainsInterface() default false;
}