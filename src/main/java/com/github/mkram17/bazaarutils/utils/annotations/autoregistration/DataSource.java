package com.github.mkram17.bazaarutils.utils.annotations.autoregistration;

import com.github.mkram17.bazaarutils.utils.annotations.modules.AutoCollect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AutoCollect("DataSources")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface DataSource {}