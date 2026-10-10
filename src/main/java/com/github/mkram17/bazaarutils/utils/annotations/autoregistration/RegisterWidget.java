package com.github.mkram17.bazaarutils.utils.annotations.autoregistration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a widget factory into ConfigUtil.getWidgets; requires public static, no arguments,
 * and a declared List or Collection return type containing AbstractWidget-compatible elements.
 * Return an empty collection when inapplicable; factory ordering is unspecified.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface RegisterWidget {
}