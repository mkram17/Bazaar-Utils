package com.github.mkram17.bazaarutils.misc.autoregistration;

import lombok.Getter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method to be called automatically during mod initialization.
 * The method must be public, static, and take no arguments.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunOnInit {
    @Getter
    enum EVENT_PRIORITIES {
        LOWEST(4),
        LOW(3),
        NORMAL(2),
        HIGH(1),
        HIGHEST(0);

        private final int value;

        EVENT_PRIORITIES(int value) {
            this.value = value;
        }
    }
    /**
     * The priority of the initialization method. Lower values run first.
     * @return the priority, defaults to 2.
     */
    EVENT_PRIORITIES priority() default EVENT_PRIORITIES.NORMAL;
}
