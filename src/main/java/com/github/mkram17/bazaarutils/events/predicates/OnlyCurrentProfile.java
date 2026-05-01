package com.github.mkram17.bazaarutils.events.predicates;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a {@code @Subscription} handler to firings of a
 * {@link com.github.mkram17.bazaarutils.events.ProfileScopedEvent} whose profile is the one
 * currently active.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnlyCurrentProfile {}