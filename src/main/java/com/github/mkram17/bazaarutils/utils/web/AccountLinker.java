package com.github.mkram17.bazaarutils.utils.web;

import com.github.mkram17.bazaarutils.config.features.WebsiteConfig;
import com.github.mkram17.bazaarutils.features.web.OrderSyncService;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.storage.LinkStorage;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.mkram17.bazaarutils.utils.PlayerActionUtil.notifyAllFromAnyThread;

/**
 * Drives the two-step link handshake behind {@code /bu link <code>}.
 *
 * <ol>
 *   <li>Tell Mojang this session is joining a "server" whose id is the link code. Only the real
 *       owner of the account can do this.</li>
 *   <li>Hand the code and the display name to the website, which asks Mojang who just joined and
 *       takes the UUID from <em>that</em> answer.</li>
 * </ol>
 *
 * <p>Step 1 must finish before step 2 starts, and promptly — Mojang keeps the join record for a
 * short window. Both steps are network calls and run off the render thread.</p>
 */
public final class AccountLinker {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    private AccountLinker() {}

    public static void link(String rawCode) {
        String code = BazaarUtilsApi.normalizeLinkCode(rawCode);

        if (code.isEmpty()) {
            notifyAllFromAnyThread(error("Enter the code shown on the website: /bu link <code>"));

            return;
        }

        MinecraftSessionUtil.Session session = MinecraftSessionUtil.currentSession().orElse(null);

        if (session == null) {
            notifyAllFromAnyThread(error("Could not read your Minecraft session. Linking requires a genuine (non-offline) login."));

            return;
        }

        if (!IN_FLIGHT.compareAndSet(false, true)) {
            notifyAllFromAnyThread(error("A link attempt is already in progress."));

            return;
        }

        notifyAllFromAnyThread(info("Verifying your Minecraft session with Mojang..."));

        // Where the request went, and which knob decided that. The value alone is not enough --
        // when it is unexpected, the next question is always "set by what?".
        BazaarUtilsApi.Endpoint endpoint = BazaarUtilsApi.resolveBaseUrl();
        Util.logMessage("Linking against %s (from %s)".formatted(endpoint.url(), endpoint.source()));

        CompletableFuture
                .runAsync(() -> joinServer(code), JsonHttpClient.executor())
                .thenCompose(ignored -> BazaarUtilsApi.confirmLink(code, session.username()))
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            handleFailure(throwable);
                        } else {
                            handleResponse(response, session);
                        }
                    } finally {
                        IN_FLIGHT.set(false);
                    }
                });
    }

    /** Chat summary of the current link, used by {@code /bu link} with no argument. */
    public static Component status() {
        if (!LinkStorage.isLinked()) {
            return Component.literal("Not linked. Generate a code at " + BazaarUtilsApi.baseUrl()
                    + "/dashboard/minecraft, then run /bu link <code>.").withStyle(ChatFormatting.GRAY);
        }

        String summary = "Linked as " + LinkStorage.linkedUsername().orElse("your account")
                + " (token " + LinkStorage.tokenPrefix().orElse("????????") + "…)";

        // Every push is filtered by the owning UUID, so a link that belongs to another Minecraft
        // account syncs nothing and says nothing. Reporting it as a working link would leave the
        // player watching a dashboard that never updates with no way to find out why. A session we
        // cannot read at all is not evidence of a mismatch, so it keeps the ordinary message.
        boolean otherAccount = MinecraftSessionUtil.currentSession()
                .map(session -> LinkStorage.tokenFor(session.profileId()).isEmpty())
                .orElse(false);

        if (otherAccount) {
            return Component.literal(summary + ", but that is a different Minecraft account than the one "
                            + "you are logged in as, so nothing will sync. Run /bu link <code> to link this "
                            + "account instead.")
                    .withStyle(ChatFormatting.YELLOW);
        }

        return Component.literal(summary + ". ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(WebsiteConfig.SYNC_ORDERS_TOGGLE
                                ? "Order syncing is on."
                                : "Order syncing is off in the settings.")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static void joinServer(String serverId) {
        try {
            MinecraftSessionUtil.joinServer(serverId);
        } catch (AuthenticationException exception) {
            throw new CompletionException(new LinkFailure(MinecraftSessionUtil.describeFailure(exception), exception));
        }
    }

    private static void handleResponse(JsonHttpClient.Response response, MinecraftSessionUtil.Session session) {
        if (!response.isSuccess()) {
            // Every failure path on the website answers with a message written for a player to
            // read, so prefer it over anything invented here.
            notifyAllFromAnyThread(error(response.errorMessage().orElseGet(() -> fallbackMessage(response.status()))));
            Util.logMessage("Link confirm failed with status " + response.status());

            return;
        }

        BazaarUtilsApi.ConfirmedLink link = response.as(BazaarUtilsApi.ConfirmedLink.class).orElse(null);

        // A 2xx that is not our API's JSON almost always means the mod is pointed at the wrong
        // host — a landing page or a proxy will happily answer 200 with HTML. Saying so beats
        // "no token", which describes the symptom and hides the cause.
        if (link == null) {
            String url = BazaarUtilsApi.baseUrl();

            notifyAllFromAnyThread(error("Got a reply from " + url
                    + " that was not a Bazaar Utils API response. Check that it points at the website."));
            Util.logError("Link confirm returned %d from %s with a non-API body: %s"
                    .formatted(response.status(), url, snippet(response.body())), null);

            return;
        }

        String token = nonBlank(link.token()).orElse(null);

        if (token == null) {
            notifyAllFromAnyThread(error("The website accepted the link but sent no token. Try again."));
            Util.logError("Link confirm succeeded without a token in the response: " + snippet(response.body()), null);

            return;
        }

        // Both of these are echoed back from Mojang's answer, so prefer them; the local session is
        // only a fallback for a response shape that changed.
        String uuid = nonBlank(link.uuid()).orElseGet(session::dashlessUuid);
        String username = nonBlank(link.username()).orElseGet(session::username);

        LinkStorage.store(token, uuid, username);

        // Clears the entitlement backoff and the last-sent payload, both of which describe a link
        // that no longer applies. Without it, buying a subscription and re-linking would still sit
        // out the rest of the hour a previous 402 imposed.
        if (BazaarUtilsModules.OrderSyncService != null) {
            BazaarUtilsModules.OrderSyncService.onLinked(link.entitled());
        }

        notifyAllFromAnyThread(Component.literal("Linked as " + username + "! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(followUp(link.entitled())).withStyle(ChatFormatting.GRAY)));
    }

    /** A field the server sent something usable in, as opposed to omitted or left blank. */
    private static Optional<String> nonBlank(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank());
    }

    /**
     * What happens next, now that the account is linked.
     *
     * <p>A link with no active subscription succeeds and then syncs nothing. Saying so here is the
     * whole point: the alternative is promising orders will sync and letting the player discover
     * otherwise from a refused push seconds later, which reads as the link itself having broken.</p>
     *
     * @param entitled null from a server that does not report it, which is not the same as "not
     *                 entitled" — an unknown answer keeps the ordinary message rather than warning
     *                 about a subscription the account may well have.
     */
    private static String followUp(@Nullable Boolean entitled) {
        if (!WebsiteConfig.SYNC_ORDERS_TOGGLE) {
            return "Turn on Website Sync in the settings to start syncing your orders.";
        }

        if (Boolean.FALSE.equals(entitled)) {
            return OrderSyncService.ENTITLEMENT_MESSAGE;
        }

        return "Your orders will sync whenever you open the Manage Orders menu.";
    }

    private static void handleFailure(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;

        if (cause instanceof LinkFailure failure) {
            notifyAllFromAnyThread(error(failure.getMessage()));

            return;
        }

        BazaarUtilsApi.Endpoint endpoint = BazaarUtilsApi.resolveBaseUrl();
        String reason = JsonHttpClient.describeTransportFailure(cause)
                .map(detail -> " — " + detail)
                .orElse(". Check your connection and try again.");

        notifyAllFromAnyThread(error("Could not reach " + endpoint.url() + reason));
        Util.logError("Link confirm request to %s (from %s) failed".formatted(endpoint.url(), endpoint.source()), cause);
    }

    private static String fallbackMessage(int status) {
        return switch (status) {
            case 400 -> "That code is invalid or has expired. Generate a new one on the website.";
            case 401 -> "Mojang could not verify your session. Make sure you are logged in and try again.";
            case 409 -> "That Minecraft account is already linked to a website account.";
            case 429 -> "Too many attempts. Wait a minute and try again.";
            default -> "The website returned an unexpected error (" + status + "). Try again later.";
        };
    }

    /** Enough of a body to recognise what answered, without dumping a whole HTML page into the log. */
    private static String snippet(String body) {
        if (body == null || body.isBlank()) return "<empty>";

        String collapsed = body.strip().replaceAll("\\s+", " ");

        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 200) + "…";
    }

    private static Component info(String message) {
        return Component.literal(message).withStyle(ChatFormatting.GRAY);
    }

    private static Component error(String message) {
        return Component.literal(message).withStyle(ChatFormatting.RED);
    }

    /** Carries an already player-readable explanation out of the async chain. */
    private static final class LinkFailure extends RuntimeException {
        private LinkFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
