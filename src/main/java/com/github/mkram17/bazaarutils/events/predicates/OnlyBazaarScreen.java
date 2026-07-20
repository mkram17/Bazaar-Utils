package com.github.mkram17.bazaarutils.events.predicates;

import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a {@code @Subscription} handler to bazaar screens. Three mutually exclusive modes
 * select which screens match:
 * <ul>
 *   <li>{@link #value()} — an explicit whitelist;</li>
 *   <li>{@link #any()} — any {@link BazaarScreenType};</li>
 *   <li>{@link #useConstraintsInterface()} — delegate to the registering instance's
 *       {@link ScreenConstrained}.</li>
 * </ul>
 * {@link #except()} additionally removes screens from the match in every mode.
 *
 * <p><strong>Note:</strong> the modes are meant to be used one at a time, but this is not
 * validated. If more than one is set, {@code BazaarScreenEventPredicateProvider} resolves them in
 * the order {@code useConstraintsInterface} &gt; {@code any} &gt; {@code value}, silently ignoring
 * the others.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnlyBazaarScreen {

    /**
     * Explicit screen whitelist.
     * Mutually exclusive with {@code any = true} and {@code useConstraintsInterface = true}.
     */
    BazaarScreenType[] value() default {};

    /**
     * Screens to exclude from matching.
     * Applies to all three modes (explicit {@code value}, {@code any}, and delegated
     * {@code useConstraintsInterface}).
     */
    BazaarScreenType[] except() default {};

    /** Match any {@link BazaarScreenType}. Mutually exclusive with {@code value()}. */
    boolean any() default false;

    /**
     * Delegate the screen list to the registering instance's
     * {@link ScreenConstrained#appliesToScreen}.
     * Mutually exclusive with {@code value()} and {@code any}.
     */
    boolean useConstraintsInterface() default false;
}