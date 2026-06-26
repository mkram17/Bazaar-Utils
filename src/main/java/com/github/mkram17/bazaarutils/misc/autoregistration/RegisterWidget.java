package com.github.mkram17.bazaarutils.misc.autoregistration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method to be automatically included in the widget registration system.
 * The annotated method must be public, static, take no arguments, and return a
 * java.util.List or java.util.Collection of ClickableWidget.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface RegisterWidget {
}
