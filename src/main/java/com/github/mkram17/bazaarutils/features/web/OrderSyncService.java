package com.github.mkram17.bazaarutils.features.web;

import com.github.mkram17.bazaarutils.config.features.WebsiteConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.storage.LinkStorage;
import com.github.mkram17.bazaarutils.utils.storage.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.web.BazaarUtilsApi;
import com.github.mkram17.bazaarutils.utils.web.JsonHttpClient;
import com.github.mkram17.bazaarutils.utils.web.MinecraftSessionUtil;
import com.github.mkram17.bazaarutils.utils.web.OrderSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed;
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pushes the tracked order list to the website after the Manage Orders menu is read.
 *
 * <p>The trigger is deliberately indirect. {@code UserOrdersChangeEvent} fires once per order
 * added or removed, so syncing on it would send a burst of requests every time the page loads.
 * Instead this marks the snapshot dirty and lets a debounced tick handler do the sending, the same
 * shape {@code DataStorage} uses for its own saves.</p>
 *
 * <p>Three guards sit in front of every push, and each one exists for a specific failure:</p>
 * <ul>
 *   <li>the stored token must belong to the <em>currently logged-in</em> account, or switching
 *       Minecraft accounts would file account B's orders under account A;</li>
 *   <li>an identical payload is never re-sent, so idling on the orders page costs nothing;</li>
 *   <li>only one request is ever in flight.</li>
 * </ul>
 */
@Module
public final class OrderSyncService extends BUListener {
    /** Matches the server's own ceiling; anything past it would be rejected as an oversized body. */
    private static final int MAX_ORDERS_PER_SYNC = 200;

    /** How long to stop trying after a 402. The subscription will not come back within seconds. */
    private static final long ENTITLEMENT_BACKOFF_MILLIS = Duration.ofHours(1).toMillis();

    private final AtomicBoolean pending = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    /** {@code uuid|body} of the last push the server accepted. */
    private volatile String lastSentKey;

    private volatile long blockedUntilMillis;

    /**
     * Runs at {@link Priority#LOW} — numerically after {@code OrderUpdater}'s {@code HIGH} — so the
     * stored order list has already been reconciled against this screen by the time we mark it dirty.
     */
    @Subscription(priority = Priority.LOW)
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    private void onOrdersPage(ContainerLoadedEvent event) {
        pending.set(true);
    }

    /**
     * The debounce. {@code @TimePassed} caps this handler at one run per window, so a page that
     * loads repeatedly still produces at most one push per 5 seconds.
     */
    @Subscription
    @TimePassed(duration = "5s")
    private void onTick(TickEvent event) {
        if (pending.compareAndSet(true, false)) {
            trySync();
        }
    }

    private void trySync() {
        if (!WebsiteConfig.SYNC_ORDERS_TOGGLE) return;

        if (System.currentTimeMillis() < blockedUntilMillis) return;

        if (inFlight.get()) {
            // Try again next window rather than queueing a second request behind the first.
            pending.set(true);

            return;
        }

        Optional<MinecraftSessionUtil.Session> session = MinecraftSessionUtil.currentSession();

        if (session.isEmpty()) return;

        Optional<String> token = LinkStorage.tokenFor(session.get().profileId());

        // Empty means either "nothing linked" or "linked to a different Minecraft account". Both
        // are ordinary states, not errors, so they stay silent.
        if (token.isEmpty()) return;

        Snapshot snapshot = collectSnapshot();
        String body = BazaarUtilsApi.serializeOrderSync(snapshot.orders());
        String key = session.get().dashlessUuid() + "|" + body;

        // The floor: sitting on the orders page must not cost a request every window.
        if (key.equals(lastSentKey)) return;

        if (!inFlight.compareAndSet(false, true)) return;

        // Logged here rather than per order, so a broken order does not write a line every window.
        if (snapshot.dropped() > 0) {
            Util.logMessage("Omitting %d order(s) from the website sync; they could not be fully parsed."
                    .formatted(snapshot.dropped()));
        }

        BazaarUtilsApi.syncOrders(token.get(), body).whenComplete((response, throwable) -> {
            try {
                if (throwable != null) {
                    // Already retried by the HTTP layer. Nothing here is worth a chat message —
                    // a dropped sync fixes itself the next time the orders page opens.
                    Util.logError("Order sync request failed", throwable);
                } else {
                    handleResponse(response, key);
                }
            } finally {
                inFlight.set(false);
            }
        });
    }

    private void handleResponse(JsonHttpClient.Response response, String key) {
        if (response.isSuccess()) {
            lastSentKey = key;
            Util.logMessage("Synced orders to the website: " + response.body());

            return;
        }

        switch (response.status()) {
            // The token was revoked, or the account was unlinked from the website. Dropping it
            // locally is what stops this from retrying forever against a dead credential.
            case 401 -> {
                LinkStorage.clear();
                lastSentKey = null;
                notifyPlayer(Component.literal("Your Bazaar Utils website link is no longer valid. Run /bu link <code> to reconnect.")
                        .withStyle(ChatFormatting.RED));
            }

            // 402 is specifically "subscription lapsed", separated from 401 so it can say so.
            case 402 -> {
                blockedUntilMillis = System.currentTimeMillis() + ENTITLEMENT_BACKOFF_MILLIS;
                notifyPlayer(Component.literal(response.errorMessage()
                                .orElse("An active Bazaar Utils subscription is required to sync orders."))
                        .withStyle(ChatFormatting.RED));
            }

            default -> Util.logError(
                    "Order sync rejected with status %d: %s".formatted(response.status(), response.body()),
                    null
            );
        }
    }

    /** What was collected, and how much of the order list did not survive the trip. */
    private record Snapshot(List<OrderSnapshot> orders, int dropped) {}

    private static Snapshot collectSnapshot() {
        List<Order> orders = UserOrdersStorage.INSTANCE.get();
        List<OrderSnapshot> collected = new ArrayList<>();
        int dropped = 0;

        for (Order order : orders) {
            if (collected.size() >= MAX_ORDERS_PER_SYNC) {
                // Never silently truncate: a capped sync would otherwise read as a complete one,
                // and the server would close every order past the cap as vanished.
                Util.logMessage("Order sync capped at %d orders; %d were not sent."
                        .formatted(MAX_ORDERS_PER_SYNC, orders.size() - MAX_ORDERS_PER_SYNC));

                break;
            }

            Optional<OrderSnapshot> snapshot = OrderSnapshot.of(order);

            if (snapshot.isPresent()) {
                collected.add(snapshot.get());
            } else {
                dropped++;
            }
        }

        return new Snapshot(collected, dropped);
    }

    /** Callbacks land on an HTTP worker; chat has to be written from the client thread. */
    private static void notifyPlayer(Component message) {
        Minecraft.getInstance().execute(() -> PlayerActionUtil.notifyAll(message));
    }
}
