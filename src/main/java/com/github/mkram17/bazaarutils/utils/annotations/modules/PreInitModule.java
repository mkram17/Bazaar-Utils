package com.github.mkram17.bazaarutils.utils.annotations.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AutoCollect("PreInitModules")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PreInitModule {}