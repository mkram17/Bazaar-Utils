package com.github.mkram17.bazaarutils.utils.annotations.modules;

import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.BazaarIntegrationCapability;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.AutoCollect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AutoCollect("Integrations")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface BazaarIntegration {
    /** Stable namespace for this integration. Multiple classes may share one id. */
    String id();
}