package com.github.mkram17.bazaarutils.utils.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Bazaar Utils website endpoints the mod talks to.
 *
 * <p>Request bodies are assembled field by field rather than reflected off a model class. The
 * order model is not safe to serialize — {@code Order} is subscribed to the event bus and holds a
 * live {@code ItemStack}, and {@code DataStorage}'s Gson has an {@code ItemStack} codec, so
 * handing it to a serializer emits full item NBT onto the wire. Building the JSON explicitly also
 * keeps this file readable next to the Zod schemas on the other end.</p>
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
        JsonObject body = new JsonObject();
        body.addProperty("code", normalizedCode);
        body.addProperty("username", username);

        return JsonHttpClient.postJson(endpoint("/api/link/confirm"), body.toString(), Map.of());
    }

    /**
     * Serializes an order snapshot into the sync request body.
     *
     * <p>Kept separate from {@link #syncOrders} so callers can compare the serialized form against
     * the last one they sent and skip an identical push.</p>
     */
    public static String serializeOrderSync(List<OrderSnapshot> orders) {
        JsonArray array = new JsonArray();
        orders.forEach(order -> array.add(order.toJson()));

        JsonObject body = new JsonObject();
        body.add("orders", array);

        return body.toString();
    }

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
