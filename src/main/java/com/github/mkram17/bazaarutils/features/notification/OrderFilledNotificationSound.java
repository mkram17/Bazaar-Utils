package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;

@Module
public class OrderFilledNotificationSound implements ToggleableFeature {
    @Override
    public boolean isEnabled() {
        return NotificationsConfig.ORDER_NOTIFICATIONS_FILLED.isEnabled();
    }

    public OrderFilledNotificationSound() {}
}
