package com.github.mkram17.bazaarutils.config.features;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.teamresourceful.resourcefulconfig.api.annotations.*;

import java.util.Arrays;

@Category(value = "developer_config")
@ConfigInfo(
        title = "Developer Config",
        titleTranslation = "bazaarutils.config.developer.category.value",
        description = "Developer configurations & toggleable tools of the mod",
        descriptionTranslation = "bazaarutils.config.developer.category.description",
        icon = "code-2"
)
public final class DeveloperConfig {
    @ConfigEntry(
            id = "enabled",
            translation = "bazaarutils.config.developer.enabled.value"
    )
    @Comment(
            value = "Global toggle for all developer related functionalities/utilities.",
            translation = "bazaarutils.config.developer.enabled.description"
    )
    public static boolean DEVELOPER_MODE_TOGGLE = false;

    @ConfigEntry(
            id = "disable_error_notifications",
            translation = "bazaarutils.config.developer.disable_error_notifications.value"
    )
    public static boolean DEVELOPER_MODE_DISABLE_ERROR_NOTIFICATIONS = false;

    @ConfigEntry(
            id = "debug_messages",
            translation = "bazaarutils.config.developer.debug_messages.value"
    )
    @Comment(
            value = "Global toggle for all developer related functionalities/utilities.",
            translation = "bazaarutils.config.developer.debug_messages.description"
    )
    @ConfigOption.Draggable
    public static NotificationType[] DEVELOPER_MODE_DEBUG_MESSAGES = new NotificationType[]{};

    public static boolean isDeveloperVariableEnabled(NotificationType type) {
        return Arrays.asList(DEVELOPER_MODE_DEBUG_MESSAGES).contains(type);
    }
}
