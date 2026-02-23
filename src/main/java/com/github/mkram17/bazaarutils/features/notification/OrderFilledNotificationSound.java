package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Module
public class OrderFilledNotificationSound implements BUToggleableFeature {
    public static boolean isEnabled() {
        return NotificationsConfig.ORDER_NOTIFICATIONS_FILLED.isEnabled();
    }
}
