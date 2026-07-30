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
 *
 * <p><strong>The values below are duplicated on the website and nothing enforces that
 * mechanically.</strong> {@code contract/wire-format.json} in this repo is the written-down copy —
 * the same file lives in the website repo, where a test holds its Zod schemas to it. Change one
 * side and you must change the file and the other side too.</p>
 *
 * <p>{@link #normalizeLinkCode} is the one where drift is invisible: the normalized code
 * <em>is</em> the {@code serverId} nonce, so a mismatch does not raise an error anywhere, it just
 * makes every link attempt fail verification.</p>
 */
public final class BazaarUtilsApi {
    private static final String DEFAULT_BASE_URL = "https://bazaarutils.dev";

    /** Points the mod at a local website during development. Unset in normal play. */
    private static final String BASE_URL_ENV = "BAZAARUTILS_API_URL";

    /**
     * The same override as a JVM system property, for launches where setting an environment
     * variable is awkward. A {@code .env} file is <em>not</em> a third option: {@link System#getenv}
     * reads the process environment only, so a {@code .env} does nothing unless something loads it
     * into that environment first (the Gradle run config does this for dev clients).
     */
    private static final String BASE_URL_PROPERTY = "bazaarutils.apiUrl";

    /**
     * Most orders one sync may carry. Matches {@code MAX_ORDERS_PER_SYNC} on the website, which
     * rejects a longer array outright, and {@code orderSync.maxOrdersPerSync} in
     * {@code contract/wire-format.json}.
     *
     * <p>Lives here rather than in {@code OrderSyncService} because it is part of the wire format,
     * not a property of the thing that happens to enforce it.</p>
     */
    public static final int MAX_ORDERS_PER_SYNC = 200;

    private BazaarUtilsApi() {}

    public static String baseUrl() {
        return resolveBaseUrl().url();
    }

    /**
     * The resolved endpoint together with where the value came from.
     *
     * <p>The source matters as much as the value when something is misconfigured: an override can
     * arrive from a system property, from the launching shell's environment, or from a {@code .env}
     * the build loaded, and knowing which one is in play is the difference between a one-line fix
     * and a hunt.</p>
     */
    public record Endpoint(String url, String source) {}

    public static Endpoint resolveBaseUrl() {
        String property = System.getProperty(BASE_URL_PROPERTY);

        if (property != null && !property.isBlank()) {
            return new Endpoint(trimTrailingSlash(property.trim()), "system property " + BASE_URL_PROPERTY);
        }

        String environment = System.getenv(BASE_URL_ENV);

        if (environment != null && !environment.isBlank()) {
            return new Endpoint(trimTrailingSlash(environment.trim()), "environment variable " + BASE_URL_ENV);
        }

        return new Endpoint(DEFAULT_BASE_URL, "built-in default");
    }

    private static String trimTrailingSlash(String url) {
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
     * <p>On success the response is <code>{ token, username, uuid, entitled }</code>. Failures
     * answer 400 (bad or expired code), 401 (Mojang could not verify the session), 409 (already
     * linked), or 429 (rate limited), each with a player-readable {@code error} field.</p>
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
     *
     * @param entitled whether the linked website account can actually receive orders. A link
     *                 succeeds without a subscription — nothing about it is wrong, it just will
     *                 not sync — so this is what lets the mod say that up front instead of
     *                 leaving the player to infer it from a refused push. {@code Boolean} rather
     *                 than {@code boolean} because a server that predates the field sends nothing,
     *                 and "not sent" must not read as "not entitled".
     */
    public record ConfirmedLink(String token, String username, String uuid, Boolean entitled) {}

    /**
     * Serializes an order snapshot into the sync request body.
     *
     * <p>Kept separate from {@link #syncOrders} so callers can compare the serialized form against
     * the last one they sent and skip an identical push.</p>
     *
     * @param partial  whether this snapshot is known not to describe every live order. The server
     *                 closes anything missing from a <em>complete</em> snapshot, so an order the
     *                 mod merely failed to parse must not be sent as one that left the Bazaar.
     * @param username the name this session is playing under, so the website can keep the display
     *                 name current. See {@link OrderSyncRequest#username()}.
     */
    public static String serializeOrderSync(List<OrderSnapshot> orders, boolean partial, String username) {
        return WebJson.GSON.toJson(new OrderSyncRequest(orders, partial, username));
    }

    /**
     * @param partial set when this snapshot is known not to describe every live order — an order
     *                that could not be parsed, or a list longer than the server accepts.
     *
     *                <p>It matters because absence is the server's only evidence that an order is
     *                gone: anything missing from a complete snapshot gets closed. Without this
     *                flag, an order the mod merely failed to <em>describe</em> looked exactly like
     *                one that had left the Bazaar, so a transient parse failure closed a live
     *                order and the next sync re-opened it as a new one. A partial snapshot updates
     *                what it does mention and closes nothing.</p>
     * @param username the display name of the session pushing this snapshot.
     *
     *                 <p>Not an identity claim — the account is resolved from the bearer token
     *                 alone, and the sync only runs when the logged-in session already matches the
     *                 token's account, so this can never name someone else's row. It exists
     *                 because players rename, and the website had no other way to find out: the
     *                 name it stored at link time was the name it showed forever.</p>
     */
    private record OrderSyncRequest(List<OrderSnapshot> orders, boolean partial, String username) {}

    /**
     * Pushes an order snapshot. The account is resolved from the bearer token alone — the payload
     * deliberately carries no identity of its own.
     *
     * <p>Answers 200 with <code>{ ok, opened, updated, closed }</code>, 401 when the token is no
     * longer usable, or 402 when the link is fine but the owning subscription is not active.</p>
     *
     * <p>Both failures carry <code>{ error, reason }</code>. The {@code reason} is the one to
     * branch on: {@code unknown_token}, {@code unlinked} and {@code revoked} all mean the stored
     * token is dead, while {@code not_entitled} means it is perfectly good and re-linking would
     * change nothing.</p>
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
