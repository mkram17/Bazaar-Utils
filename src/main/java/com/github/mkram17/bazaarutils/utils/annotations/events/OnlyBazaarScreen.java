package com.github.mkram17.bazaarutils.utils.annotations.events;

import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnlyBazaarScreen {
    BazaarScreenType[] value() default {};
    boolean all() default false;
}