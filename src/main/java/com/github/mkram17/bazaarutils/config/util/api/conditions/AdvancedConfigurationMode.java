package com.github.mkram17.bazaarutils.config.util.api.conditions;

import com.github.mkram17.bazaarutils.config.BUConfig;

import java.util.Optional;

/**
 * A {@link ConfigCondition} that shows a field only when advanced configuration
 * mode is enabled. Reads a static config flag; the {@code instance} parameter
 * is ignored.
 */
public final class AdvancedConfigurationMode implements ConfigCondition {
    @Override
    public boolean shouldShow(Optional<?> instance) {
        return BUConfig.ADVANCED_CONFIGURATION_TOGGLE.get();
    }
}
