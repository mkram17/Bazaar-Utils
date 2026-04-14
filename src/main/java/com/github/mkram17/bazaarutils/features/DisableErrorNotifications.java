package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;

@Module
public class DisableErrorNotifications implements ToggleableFeature {
    @Override
    public boolean isEnabled() {
        return DeveloperConfig.DEVELOPER_MODE_TOGGLE && DeveloperConfig.DEVELOPER_MODE_DISABLE_ERROR_NOTIFICATIONS;
    }

    public DisableErrorNotifications() {}
}
