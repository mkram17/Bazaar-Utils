package com.github.mkram17.bazaarutils.utils.annotations.modules;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.AutoCollect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AutoCollect("Commands")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Command {
    Class<? extends BUCommand> parent() default BUCommand.class;
}