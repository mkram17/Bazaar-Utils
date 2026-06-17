package com.github.mkram17.bazaarutils.features.notification.order;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderPositionEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.features.notification.*;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.github.mkram17.bazaarutils.features.notification.NotificationChannelType.*;

@Module
public final class OrderNotificationHandler extends BUListener implements ToggleableFeature {

    private static final BazaarLogger LOG = BazaarLogger.of(OrderNotificationHandler.class);

    public OrderNotificationHandler() {
        NotificationBus.registerDispatcher(
                NotificationsConfig.OrderNotificationSettings.class,
                new NotificationBus.ChannelDispatcher<NotificationsConfig.OrderNotificationSettings>(s -> s.channels)
                        .batchWhen(() -> NotificationsConfig.BATCH_ORDER_NOTIFICATIONS_TOGGLE)
                        .on(CHAT, (notification, payload) -> PlayerLogger.sendWithCommand(
                                Component.empty()
                                        .append(payload.content().chatComponent())
                                        .append(" ")
                                        .append(notification.clickCommand.clickHint()),
                                notification.clickCommand.commandFor(payload.subject().label())))
                        .on(SCREEN, (notification, payload) -> PlayerLogger.sendTitle(
                                payload.content().screenTitle().title(),
                                payload.content().screenTitle().subtitle()))
                        .on(SOUND, (notification, payload) -> PlayerLogger.playSound(notification))
                        .on(COMMAND, (notification, payload) -> notification.autoCommand.run(payload.subject().label()))
                        .on(OS, (notification, payload) -> NotificationBus.osNotify(payload.content().plainText()))
                        .on(REMOTE, (notification, payload) -> remoteDispatch(notification.webhookUrl, payload.content().discordPayload(), payload.kind()))
        );

        for (var kind : OrderNotificationKind.values()) {
            NotificationBus.registerBatchFormatter(kind, (count, rep) -> OrderNotificationFormatter.batched(count, rep, kind));
        }
    }

    @Override
    public boolean isEnabled() {
        return NotificationsConfig.NOTIFICATIONS_TOGGLE;
    }

    private void post(Order order, OrderNotificationKind kind, NotificationPayload.Content content) {
        if (order.coopOrder() && !NotificationsConfig.COOP_ORDER_NOTIFICATIONS_TOGGLE) return;
        NotificationBus.order(kind, OrderNotificationFormatter.subjectOf(order), content);
    }

    private static OrderNotificationKind kindOf(PricingPosition pos) {
        return switch (pos) {
            case COMPETITIVE -> OrderNotificationKind.COMPETITIVE;
            case MATCHED -> OrderNotificationKind.MATCHED;
            case OUTBID -> OrderNotificationKind.OUTBID;
        };
    }

    private final Set<UUID> recentlyPlaced = new HashSet<>();

    @Subscription
    public void onOrderPlacedTracking(UserOrderEvent.Placed event) {
        recentlyPlaced.add(event.getOrder().id());
    }

    @Subscription
    public void onOrderTerminated(UserOrderEvent event) {
        if (event instanceof UserOrderEvent.Filled || event instanceof UserOrderEvent.Cancelled) {
            recentlyPlaced.remove(event.getOrder().id());
        }
    }

    @Subscription
    @OnlyWhenEnabled
    public void onPositionChange(UserOrderPositionEvent event) {
        var current = event.getPosition().classify(NotificationsConfig.SELF_OUTBID_TOGGLE);

        if (event.getPreviousPosition() == null) {
            // previousPosition == null = first check this session.
            // Only a genuine placement (tracked via Placed event) qualifies for the
            // placement notification toggle. Session-resumes are always suppressed.
            if (!recentlyPlaced.remove(event.getOrder().id())) return;
            if (!NotificationsConfig.NOTIFY_POSITION_ON_PLACEMENT_TOGGLE) return;
        } else if (event.getPreviousPosition().classify(NotificationsConfig.SELF_OUTBID_TOGGLE) == current) {
            return;
        }

        Order order = event.getOrder();
        NotificationPayload.Content content = switch (current) {
            case COMPETITIVE -> OrderNotificationFormatter.competitive(order);
            case MATCHED     -> OrderNotificationFormatter.matched(order);
            case OUTBID      -> OrderNotificationFormatter.outbid(order);
        };

        post(order, kindOf(current), content);
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderPlaced(UserOrderEvent.Placed event) {
        post(event.getOrder(), OrderNotificationKind.PLACED, OrderNotificationFormatter.placed(event.getOrder()));
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderPartiallyFilled(UserOrderEvent.PartiallyFilled event) {
        post(event.getOrder(), OrderNotificationKind.PARTIALLY_FILLED, OrderNotificationFormatter.partiallyFilled(event));
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderFilled(UserOrderEvent.Filled event) {
        post(event.getOrder(), OrderNotificationKind.FILLED, OrderNotificationFormatter.filled(event.getOrder()));
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderClaimed(UserOrderEvent.Claimed event) {
        post(event.getOrder(), OrderNotificationKind.CLAIMED, OrderNotificationFormatter.claimed(event));
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderFlipped(UserOrderEvent.Flipped event) {
        post(event.getOrder(), OrderNotificationKind.FLIPPED, OrderNotificationFormatter.flipped(event));
    }

    @Subscription
    @OnlyWhenEnabled
    public void onOrderCancelled(UserOrderEvent.Cancelled event) {
        post(event.getOrder(), OrderNotificationKind.CANCELLED, OrderNotificationFormatter.cancelled(event.getOrder()));
    }

    private static void remoteDispatch(String url, DiscordPayload payload, NotificationKind kind) {
        if (url == null || url.isBlank()) {
            return;
        }

        String body = payload.toJson();

        CompletableFuture.runAsync(() -> {
            try {
                var conn = (HttpURLConnection) new URI(url).toURL().openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", BazaarUtils.MOD_NAME);
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                conn.setDoOutput(true);

                try {
                    try (var out = conn.getOutputStream()) {
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }

                    int code = conn.getResponseCode();

                    if (code < 200 || code >= 300) LOG.warn("Remote dispatch for {} returned HTTP {}", kind, code);
                } finally {
                    conn.disconnect();
                }
            } catch (Throwable t) {
                LOG.warn("Remote dispatch for {} failed: {}", kind, t.getMessage());
            }
        });
    }
}