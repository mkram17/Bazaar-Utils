package com.github.mkram17.bazaarutils.config.features.notification;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.github.mkram17.bazaarutils.config.util.api.conditions.AdvancedConfigurationMode;
import com.github.mkram17.bazaarutils.config.util.api.conditions.MethodEquals;
import com.github.mkram17.bazaarutils.features.notification.NotificationChannelType;
import com.github.mkram17.bazaarutils.features.notification.OrderNotificationKind;
import com.github.mkram17.bazaarutils.utils.bazaar.BazaarChatCommand;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.AudioSource;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundHolder;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.SoundRef;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.features.notification.NotificationChannelType.*;

@Category(value = "notifications_config")
@ConfigInfo(
        title = "Notifications Config",
        titleTranslation = "bazaarutils.config.notifications.category.label",
        description = "Configurations for the notifications features of the mod",
        descriptionTranslation = "bazaarutils.config.notifications.category.hint",
        icon = "bell"
)
public class NotificationsConfig {

    @ConfigEntry(
            id = "enabled",
            translation = "bazaarutils.config.notifications.enabled.label"
    )
    @Comment(
            value = "Produces notifications to various configurable channels in response to market & order events.",
            translation = "bazaarutils.config.notifications.enabled.hint"
    )
    @ConfigOption.Separator(value = "bazaarutils.config.notifications.separator.introductory.label")
    public static boolean NOTIFICATIONS_TOGGLE = false;

    @ConfigEntry(
            id = "deduplication_window_ticks",
            translation = "bazaarutils.config.notifications.deduplication_window_ticks.label"
    )
    @Comment(
            value = "Within this many ticks, events sharing the same kind and subject are coalesced " +
                    "into one notification. Set to 0 to disable. (20 ticks = 1 second)",
            translation = "bazaarutils.config.notifications.deduplication_window_ticks.hint"
    )
    @ConfigOption.Slider()
    @ConfigOption.Range(min = 0, max = 500)
    public static int DEDUPLICATION_WINDOW_TICKS = 10;

    @ConfigEntry(id = "order_bus_informational_separator")
    @ConfigOption.Hidden
    @ConfigOption.Separator(
            value = "bazaarutils.config.notifications.separator.order_bus_informational.label",
            description = "bazaarutils.config.notifications.separator.order_bus_informational.hint"
    )
    public static boolean ORDER_BUS_INFORMATIONAL_SEPARATOR = true;

    @ConfigEntry(
            id = "coop_order_notifications",
            translation = "bazaarutils.config.notifications.coop_orders.label"
    )
    @Comment(
            value = "Include notifications for co-op members' orders.",
            translation = "bazaarutils.config.notifications.coop_orders.hint"
    )
    public static boolean COOP_ORDER_NOTIFICATIONS_TOGGLE = true;

    @ConfigEntry(
            id = "batch_order_notifications",
            translation = "bazaarutils.config.notifications.batch_orders.label"
    )
    @Comment(
            value = "When multiple notifications of the same kind and product fire within the " +
                    "deduplication window, collapse them into one summary message. " +
                    "Example: \"12 buy orders for 71,680 Nether Wart have been outbid.\"",
            translation = "bazaarutils.config.notifications.batch_orders.hint"
    )
    @ShowIf(AdvancedConfigurationMode.class)
    public static boolean BATCH_ORDER_NOTIFICATIONS_TOGGLE = true;

    @ConfigEntry(
            id = "self_outbid",
            translation = "bazaarutils.config.notifications.self_outbid.label"
    )
    @Comment(
            value = "By default, your own volume at a price level doesn't count against you — so a " +
                    "spot you hold alone still reports as COMPETITIVE. Enable this if you'd rather any " +
                    "shared price level fire MATCHED notifications regardless of who owns the volume.",
            translation = "bazaarutils.config.notifications.self_outbid.hint"
    )
    @ShowIf(AdvancedConfigurationMode.class)
    public static boolean SELF_OUTBID_TOGGLE = false;

    @ConfigEntry(
            id = "notify_position_on_placement",
            translation = "bazaarutils.config.notifications.notify_position_on_placement.label"
    )
    @Comment(
            value = "Whether COMPETITIVE/MATCHED/OUTBID notifications fire for an order's very first " +
                    "position check, right after it's placed. Disable this to only be notified when " +
                    "your position changes later due to market activity, not on the initial placement.",
            translation = "bazaarutils.config.notifications.notify_position_on_placement.hint"
    )
    @ShowIf(AdvancedConfigurationMode.class)
    public static boolean NOTIFY_POSITION_ON_PLACEMENT_TOGGLE = false;

    @ConfigEntry(
            id = "order_notifications",
            translation = "bazaarutils.config.notifications.order_notifications.label"
    )
    @Comment(
            value = "Each configured notification specifies which order events it triggers by " +
                    "and which channels deliver it. Multiple rules may trigger by the same event.",
            translation = "bazaarutils.config.notifications.order_notifications.hint"
    )
    public static final List<OrderNotificationSettings> ORDER_NOTIFICATIONS = new ArrayList<>(List.of());

    public static List<OrderNotificationSettings> forOrderNotificationKind(OrderNotificationKind kind) {
        return ORDER_NOTIFICATIONS.stream()
                .filter(rule -> rule.appliesToKind(kind))
                .toList();
    }

    @ConfigObject
    public static final class OrderNotificationSettings implements ListEntryInfoProvider, SoundHolder {
        @ConfigEntry(
                id = "kinds",
                translation = "bazaarutils.config.notifications.notification.kinds.label"
        )
        @Comment(
                value = "The order events this notification triggers by.",
                translation = "bazaarutils.config.notifications.notification.kinds.hint"
        )
        public OrderNotificationKind[] kinds;

        @ConfigEntry(
                id = "channels",
                translation = "bazaarutils.config.notifications.notification.channels.label"
        )
        @Comment(
                value = "Output channels for this notification.",
                translation = "bazaarutils.config.notifications.notification.channels.hint"
        )
        public NotificationChannelType[] channels;

        @ConfigEntry(
                id = "click_command",
                translation = "bazaarutils.config.notifications.notification.click_command.label"
        )
        @Comment(
                value = "Command attached as a clickable event to the chat message.",
                translation = "bazaarutils.config.notifications.notification.click_command.hint"
        )
        @ConfigOption.Separator(
                value = "bazaarutils.config.notifications.notification.separator.chat_channel.label",
                description = "bazaarutils.config.notifications.notification.separator.chat_channel.hint"
        )
        @ShowIf(OrderNotificationSettings.WhenChatChannelSelected.class)
        public BazaarChatCommand clickCommand = BazaarChatCommand.SEARCH_ITEM;

        @ConfigEntry(
                id = "fade_in_time",
                translation = "bazaarutils.config.notifications.notification.fade_in_time.label"
        )
        @Comment(
                value = "The duration in milliseconds it takes for the screen title to fade in entirely.",
                translation = "bazaarutils.config.notifications.notification.fade_in_time.hint"
        )
        @ConfigOption.Separator(
                value = "bazaarutils.config.notifications.notification.separator.screen_channel.label",
                description = "bazaarutils.config.notifications.notification.separator.screen_channel.hint"
        )
        @ShowIf({ OrderNotificationSettings.WhenScreenChannelSelected.class, AdvancedConfigurationMode.class })
        public int fadeInTime = 10;

        @ConfigEntry(
                id = "stay_time",
                translation = "bazaarutils.config.notifications.notification.stay_time.label"
        )
        @Comment(
                value = "The duration in milliseconds it takes for the screen title to start to fade out.",
                translation = "bazaarutils.config.notifications.notification.stay_time.hint"
        )
        @ShowIf({ OrderNotificationSettings.WhenScreenChannelSelected.class, AdvancedConfigurationMode.class })
        public int stayTime = 40;

        @ConfigEntry(
                id = "fade_out_time",
                translation = "bazaarutils.config.notifications.notification.fade_out_time.label"
        )
        @Comment(
                value = "The duration in milliseconds it takes for screen title to fade out entirely.",
                translation = "bazaarutils.config.notifications.notification.fade_out_time.hint"
        )
        @ShowIf({ OrderNotificationSettings.WhenScreenChannelSelected.class, AdvancedConfigurationMode.class })
        public int fadeOutTime = 20;

        @ConfigEntry(
                id = "sound_id",
                translation = "bazaarutils.config.notifications.notification.sound_id.label"
        )
        @Comment(
                value = "The sound which is played.",
                translation = "bazaarutils.config.notifications.notification.sound_id.hint"
        )
        @ConfigOption.Separator(
                value = "bazaarutils.config.notifications.notification.separator.sound_channel.label",
                description = "bazaarutils.config.notifications.notification.separator.sound_channel.hint"
        )
        @ConfigOption.Renderer("bazaarutils:sound")
        @ShowIf(OrderNotificationSettings.WhenSoundChannelSelected.class)
        public String soundId = "minecraft:ui.cartography_table.take_result";

        @Override
        public SoundRef getSoundRef() {
            return SoundRef.of(() -> soundId);
        }

        @ConfigEntry(
                id = "sound_volume",
                translation = "bazaarutils.config.notifications.notification.sound_volume.label"
        )
        @Comment(
                value = "The volume with which the sound is played.",
                translation = "bazaarutils.config.notifications.notification.sound_volume.hint"
        )
        @ConfigOption.Slider
        @ConfigOption.Range(min = 0.0f, max = 1f)
        @ShowIf(OrderNotificationSettings.WhenSoundChannelSelected.class)
        public float soundVolume = 0.2f;

        @Override
        public float getSoundVolume() {
            return soundVolume;
        }

        @ConfigEntry(
                id = "sound_source",
                translation = "bazaarutils.config.notifications.notification.sound_source.label"
        )
        @Comment(
                value = "Configurable channel this sound plays through (Master, Ambient, Block, Player, UI, etc.).",
                translation = "bazaarutils.config.notifications.notification.sound_source.hint"
        )
        @ShowIf(WhenSoundChannelSelected.class)
        public AudioSource soundSource = AudioSource.UI;

        @Override
        public AudioSource getAudioSource() {
            return soundSource;
        }

        @ConfigEntry(
                id = "auto_command",
                translation = "bazaarutils.config.notifications.notification.auto_command.label"
        )
        @Comment(
                value = "Command run automatically when this notification triggers.",
                translation = "bazaarutils.config.notifications.notification.auto_command.hint"
        )
        @ConfigOption.Separator(
                value = "bazaarutils.config.notifications.notification.separator.command_channel.label",
                description = "bazaarutils.config.notifications.notification.separator.command_channel.hint"
        )
        @ShowIf(OrderNotificationSettings.WhenCommandChannelSelected.class)
        public BazaarChatCommand autoCommand = BazaarChatCommand.OPEN_ORDERS;

        @ConfigEntry(
                id = "webhook_url",
                translation = "bazaarutils.config.notifications.notification.webhook_url.label"
        )
        @Comment(
                value = "HTTP endpoint to POST this notification to (e.g. a Discord webhook URL).",
                translation = "bazaarutils.config.notifications.notification.webhook_url.hint"
        )
        @ConfigOption.Separator(
                value = "bazaarutils.config.notifications.notification.separator.remote_channel.label",
                description = "bazaarutils.config.notifications.notification.separator.remote_channel.hint"
        )
        @ShowIf(OrderNotificationSettings.WhenRemoteChannelSelected.class)
        public String webhookUrl = "";

        public boolean appliesToKind(OrderNotificationKind kind) {
            for (var k : kinds) if (k == kind) return true;

            return false;
        }

        public static Predicate<OrderNotificationSettings> isChannelEnabled(NotificationChannelType target) {
            return notification -> {
                if (notification.channels == null) return false;

                for (var channel : notification.channels) if (channel == target) return true;

                return false;
            };
        }

        public static final class WhenChatChannelSelected extends MethodEquals<OrderNotificationSettings, Boolean> {
            public WhenChatChannelSelected() {
                super(OrderNotificationSettings.class, notification -> isChannelEnabled(CHAT).test(notification), Boolean.TRUE);
            }
        }

        public static final class WhenScreenChannelSelected extends MethodEquals<OrderNotificationSettings, Boolean> {
            public WhenScreenChannelSelected() {
                super(OrderNotificationSettings.class, notification -> isChannelEnabled(SCREEN).test(notification), Boolean.TRUE);
            }
        }

        public static final class WhenSoundChannelSelected extends MethodEquals<OrderNotificationSettings, Boolean> {
            public WhenSoundChannelSelected() {
                super(OrderNotificationSettings.class, notification -> isChannelEnabled(SOUND).test(notification), Boolean.TRUE);
            }
        }

        public static final class WhenCommandChannelSelected extends MethodEquals<OrderNotificationSettings, Boolean> {
            public WhenCommandChannelSelected() {
                super(OrderNotificationSettings.class, notification -> isChannelEnabled(COMMAND).test(notification), Boolean.TRUE);
            }
        }

        public static final class WhenRemoteChannelSelected extends MethodEquals<OrderNotificationSettings, Boolean> {
            public WhenRemoteChannelSelected() {
                super(OrderNotificationSettings.class, notification -> isChannelEnabled(REMOTE).test(notification), Boolean.TRUE);
            }
        }

        @Override
        public Component getTitle(int index) {
            if (kinds == null || kinds.length == 0) return Component.literal("(no kinds)");

            return Component.literal(
                    Arrays.stream(kinds)
                            .map(k -> k.name().charAt(0) + k.name().substring(1).toLowerCase().replace('_', ' '))
                            .collect(Collectors.joining(", ")));
        }

        @Override
        public Component getDescription(int index) {
            if (channels == null || channels.length == 0) return Component.literal("No channels");

            return Component.literal("Channels: " + Arrays.stream(channels).map(Enum::name).collect(Collectors.joining(", ")));
        }

        public OrderNotificationSettings(OrderNotificationKind[] kinds, NotificationChannelType[] channels) {
            this.kinds = kinds;
            this.channels = channels;
        }

        public OrderNotificationSettings() {
            this(new OrderNotificationKind[]{}, new NotificationChannelType[]{});
        }
    }
}