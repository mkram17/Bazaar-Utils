package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Module
public class DisableErrorNotifications implements BUToggleableFeature {
    public static boolean isEnabled() {
        return DeveloperConfig.DEVELOPER_MODE_TOGGLE && DeveloperConfig.DEVELOPER_MODE_DISABLE_ERROR_NOTIFICATIONS;
    }

    public DisableErrorNotifications() {}
}
