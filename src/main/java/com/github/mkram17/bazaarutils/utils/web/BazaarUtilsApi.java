package com.github.mkram17.bazaarutils.utils.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Bazaar Utils website endpoints the mod talks to.
 *
 * <p>Every body is a record serialized by {@link WebJson}, so the record declarations are the
 * protocol: they line up one-for-one with the Zod schemas on the other end. What is deliberately
 * absent is any model object — {@code Order} is subscribed to the event bus and holds a live
 * {@code ItemStack}, so it is converted to {@link OrderSnapshot} first rather than serialized.</p>
 */
public final class BazaarUtilsApi {
    private static final String DEFAULT_BASE_URL = "https://bazaarutils.dev";

    /** Points the mod at a local website during development. Unset in normal play. */
    private static final String BASE_URL_ENV = "BAZAARUTILS_API_URL";

    private BazaarUtilsApi() {}

    public static String baseUrl() {
        String override = System.getenv(BASE_URL_ENV);
        String url = (override != null && !override.isBlank()) ? override.trim() : DEFAULT_BASE_URL;

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Normalizes a link code exactly as the website's {@code normalizeLinkCode} does.
     *
     * <p>This has to match character for character. The normalized code <em>is</em> the
     * {@code serverId} nonce: the mod joins with it and the website looks it up with it, so any
     * divergence turns every link attempt into a silent verification failure.</p>
     */
    public static String normalizeLinkCode(String input) {
        return input.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    /**
     * Completes a link. Unauthenticated by necessity — the mod has no website session, so the code
     * is what proves which account is being linked.
     *
     * <p>The body carries no UUID. The username is sent only as the {@code hasJoined} lookup key;
     * the authoritative identity comes back from Mojang, via the website's response.</p>
     *
     * <p>On success the response is <code>{ token, username, uuid }</code>. Failures answer 400
     * (bad or expired code), 401 (Mojang could not verify the session), 409 (already linked), or
     * 429 (rate limited), each with a player-readable {@code error} field.</p>
     */
    public static CompletableFuture<JsonHttpClient.Response> confirmLink(String normalizedCode, String username) {
        String body = WebJson.GSON.toJson(new LinkConfirmRequest(normalizedCode, username));

        return JsonHttpClient.postJson(endpoint("/api/link/confirm"), body, Map.of());
    }

    private record LinkConfirmRequest(String code, String username) {}

    /**
     * A completed link, as the website reports it.
     *
     * <p>The UUID and username here are the ones Mojang returned from {@code hasJoined}, not
     * anything the mod sent. Every component can be null if the response shape ever changes, so
     * callers must check rather than trust.</p>
     */
    public record ConfirmedLink(String token, String username, String uuid) {}

    /**
     * Serializes an order snapshot into the sync request body.
     *
     * <p>Kept separate from {@link #syncOrders} so callers can compare the serialized form against
     * the last one they sent and skip an identical push.</p>
     */
    public static String serializeOrderSync(List<OrderSnapshot> orders) {
        return WebJson.GSON.toJson(new OrderSyncRequest(orders));
    }

    private record OrderSyncRequest(List<OrderSnapshot> orders) {}

    /**
     * Pushes an order snapshot. The account is resolved from the bearer token alone — the payload
     * deliberately carries no identity of its own.
     *
     * <p>Answers 200 with <code>{ ok, opened, updated, closed }</code>, 401 when the token has
     * been revoked or the account unlinked, or 402 when the owning subscription has lapsed.</p>
     */
    public static CompletableFuture<JsonHttpClient.Response> syncOrders(String token, String body) {
        return JsonHttpClient.postJson(
                endpoint("/api/orders/sync"),
                body,
                Map.of("Authorization", "Bearer " + token)
        );
    }

    private static URI endpoint(String path) {
        return URI.create(baseUrl() + path);
    }
}
