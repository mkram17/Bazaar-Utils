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
 *
 * <p>A snapshot that could not describe every order is flagged {@code partial}, which tells the
 * server not to read the gaps as orders that left the Bazaar. Absence is the only close signal
 * there is, so an unflagged partial snapshot closes live orders and the next sync re-opens them
 * as new ones.</p>
 */
@Module
public final class OrderSyncService extends BUListener {
    /** The wire contract's ceiling. Declared in {@link BazaarUtilsApi} because it is protocol. */
    private static final int MAX_ORDERS_PER_SYNC = BazaarUtilsApi.MAX_ORDERS_PER_SYNC;

    /** How long to stop trying after a 402. The subscription will not come back within seconds. */
    private static final long ENTITLEMENT_BACKOFF_MILLIS = Duration.ofHours(1).toMillis();

    /**
     * Said when a link is good but nothing will sync under it.
     *
     * <p>Shared with {@link com.github.mkram17.bazaarutils.utils.web.AccountLinker} so linking
     * without a subscription and syncing without one describe the same state the same way. It
     * deliberately does not mention re-linking: the link is not the problem.</p>
     */
    public static final String ENTITLEMENT_MESSAGE = "Order syncing is paused: an active Bazaar Utils "
            + "subscription is required to sync new orders. Your existing history stays available.";

    private final AtomicBoolean pending = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    /** {@code uuid|body} of the last push the server accepted. */
    private volatile String lastSentKey;

    private volatile long blockedUntilMillis;

    /** Whether the player has already been told syncing is paused for want of a subscription. */
    private volatile boolean entitlementAnnounced;

    /**
     * Lifts the {@link #ENTITLEMENT_BACKOFF_MILLIS} pause and forgets the deduplication state.
     *
     * <p>Called after a successful link. Both pieces of state describe a link that no longer
     * applies: without clearing them, subscribing and re-linking would still sit out the rest of
     * the hour, and the first snapshot under the new link could be skipped as a duplicate of one
     * pushed under the old one.</p>
     *
     * @param entitled what the website reported about the freshly linked account, or empty from a
     *                 server that does not say. A known-unentitled link has already been explained
     *                 in the link message itself, so the 402 the next push is certain to get must
     *                 not repeat it — the player would see the same thing twice, seconds apart.
     */
    public void onLinked(Optional<Boolean> entitled) {
        blockedUntilMillis = 0;
        lastSentKey = null;
        entitlementAnnounced = entitled.filter(value -> !value).isPresent();
        pending.set(true);
    }

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
        String body = BazaarUtilsApi.serializeOrderSync(
                snapshot.orders(), snapshot.partial(), session.get().username());
        String key = session.get().dashlessUuid() + "|" + body;

        // The floor: sitting on the orders page must not cost a request every window.
        if (key.equals(lastSentKey)) return;

        if (!inFlight.compareAndSet(false, true)) return;

        // Logged here rather than per order, so a broken order does not write a line every window.
        if (snapshot.dropped() > 0) {
            Util.logMessage(("Omitting %d order(s) from the website sync; they could not be fully "
                    + "parsed. Marking the snapshot partial so the website does not treat them as closed.")
                    .formatted(snapshot.dropped()));
        }

        BazaarUtilsApi.syncOrders(token.get(), body).whenComplete((response, throwable) -> {
            try {
                if (throwable != null) {
                    // Already retried by the HTTP layer. Nothing here is worth a chat message —
                    // a dropped sync fixes itself the next time the orders page opens.
                    Util.logError("Order sync request failed", throwable);
                } else {
                    handleResponse(response, key, token.get());
                }
            } finally {
                inFlight.set(false);
            }
        });
    }

    /**
     * @param token the token this response answers, which is not necessarily the one stored now
     */
    private void handleResponse(JsonHttpClient.Response response, String key, String token) {
        if (response.isSuccess()) {
            lastSentKey = key;
            Util.logMessage("Synced orders to the website: " + response.body());

            return;
        }

        switch (response.status()) {
            // The token is no longer usable: unknown, superseded by a newer install, or the
            // account was unlinked. Dropping it locally is what stops this from retrying forever
            // against a dead credential.
            case 401 -> {
                // A rejection can outlive the link it was sent under — re-linking while a sync is
                // in flight is exactly how, and the window is wide because onLinked() queues a
                // push immediately. Clearing on a stale rejection would delete the link the player
                // just made and tell them it had gone bad, while the website still shows it as
                // connected.
                if (!LinkStorage.isStoredToken(token)) {
                    Util.logMessage("Ignoring a rejected sync for a token this install no longer "
                            + "holds; the link was replaced or removed while the request was in flight.");

                    return;
                }

                LinkStorage.clear();
                lastSentKey = null;
                notifyPlayer(Component.literal(reconnectMessage(response)).withStyle(ChatFormatting.RED));
            }

            // 402 is specifically "the link is fine, the subscription is not", separated from 401
            // so it can say so — and so it does not throw away a working link.
            case 402 -> {
                blockedUntilMillis = System.currentTimeMillis() + ENTITLEMENT_BACKOFF_MILLIS;

                // Said once per link rather than once per backoff window: the state does not
                // change on its own, so repeating it only adds noise to a player who cannot act
                // on it from in game.
                if (!entitlementAnnounced) {
                    entitlementAnnounced = true;
                    notifyPlayer(Component.literal(response.errorMessage().orElse(ENTITLEMENT_MESSAGE))
                            .withStyle(ChatFormatting.RED));
                }
            }

            default -> Util.logError(
                    "Order sync rejected with status %d: %s".formatted(response.status(), response.body()),
                    null
            );
        }
    }

    /**
     * What to tell a player whose token was refused.
     *
     * <p>Keyed off the server's {@code reason} rather than its prose, so the text can name the
     * actual cause. The old single message asserted the link had gone invalid, which was a guess:
     * a 401 covers an unlinked account and a token superseded by another install just as much.</p>
     */
    private static String reconnectMessage(JsonHttpClient.Response response) {
        String cause = switch (response.errorReason().orElse("")) {
            case "unlinked" -> "This Minecraft account was unlinked from your Bazaar Utils account.";
            case "revoked" -> "This install was disconnected because the account was linked somewhere else.";
            case "unknown_token" -> "This install is no longer linked to a Bazaar Utils account.";
            // Older server, or a reason this build does not know. Describe the symptom and stop
            // short of naming a cause, rather than inventing one.
            default -> "The website would not accept your Bazaar Utils link.";
        };

        return cause + " Run /bu link <code> to reconnect.";
    }

    /**
     * What was collected, and whether it is the whole picture.
     *
     * @param dropped   orders that could not be described well enough to send
     * @param truncated whether the list was cut short at {@link #MAX_ORDERS_PER_SYNC}
     */
    private record Snapshot(List<OrderSnapshot> orders, int dropped, boolean truncated) {
        /**
         * Whether the server must not read absence as "this order is gone".
         *
         * <p>Orders are parsed off item lore, so failing to describe one is a normal, transient
         * outcome — but on the wire it is indistinguishable from an order that left the Bazaar.
         * Saying so explicitly is what stops a parse failure from closing a live order and the
         * sync after it re-opening the same order as a new row.</p>
         */
        boolean partial() {
            return dropped > 0 || truncated;
        }
    }

    private static Snapshot collectSnapshot() {
        List<Order> orders = UserOrdersStorage.INSTANCE.get();
        List<OrderSnapshot> collected = new ArrayList<>();
        int dropped = 0;
        boolean truncated = false;

        for (Order order : orders) {
            if (collected.size() >= MAX_ORDERS_PER_SYNC) {
                Util.logMessage("Order sync capped at %d orders; %d were not sent."
                        .formatted(MAX_ORDERS_PER_SYNC, orders.size() - MAX_ORDERS_PER_SYNC));

                truncated = true;

                break;
            }

            Optional<OrderSnapshot> snapshot = OrderSnapshot.of(order);

            if (snapshot.isPresent()) {
                collected.add(snapshot.get());
            } else {
                dropped++;
            }
        }

        return new Snapshot(collected, dropped, truncated);
    }

    /** Callbacks land on an HTTP worker; chat has to be written from the client thread. */
    private static void notifyPlayer(Component message) {
        Minecraft.getInstance().execute(() -> PlayerActionUtil.notifyAll(message));
    }
}
