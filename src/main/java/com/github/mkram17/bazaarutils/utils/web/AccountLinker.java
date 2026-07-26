package com.github.mkram17.bazaarutils.utils.web;

import com.github.mkram17.bazaarutils.config.features.WebsiteConfig;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.storage.LinkStorage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

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
            notifyPlayer(error("Enter the code shown on the website: /bu link <code>"));

            return;
        }

        Optional<MinecraftSessionUtil.Session> session = MinecraftSessionUtil.currentSession();

        if (session.isEmpty()) {
            notifyPlayer(error("Could not read your Minecraft session. Linking requires a genuine (non-offline) login."));

            return;
        }

        if (!IN_FLIGHT.compareAndSet(false, true)) {
            notifyPlayer(error("A link attempt is already in progress."));

            return;
        }

        notifyPlayer(info("Verifying your Minecraft session with Mojang..."));

        CompletableFuture
                .runAsync(() -> joinServer(code), JsonHttpClient.executor())
                .thenCompose(ignored -> BazaarUtilsApi.confirmLink(code, session.get().username()))
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            handleFailure(throwable);
                        } else {
                            handleResponse(response, session.get());
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

        String username = LinkStorage.linkedUsername().orElse("your account");
        String prefix = LinkStorage.tokenPrefix().orElse("????????");

        return Component.literal("Linked as " + username + " (token " + prefix + "…). ")
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
            notifyPlayer(error(response.errorMessage().orElseGet(() -> fallbackMessage(response.status()))));
            Util.logMessage("Link confirm failed with status " + response.status());

            return;
        }

        Optional<JsonObject> body = response.json();
        String token = string(body, "token").orElse(null);

        if (token == null) {
            notifyPlayer(error("The website accepted the link but sent no token. Try again."));
            Util.logError("Link confirm succeeded without a token in the response", null);

            return;
        }

        // Both of these are echoed back from Mojang's answer, so prefer them; the local session is
        // only a fallback for a response shape that changed.
        String uuid = string(body, "uuid").orElseGet(session::dashlessUuid);
        String username = string(body, "username").orElseGet(session::username);

        LinkStorage.store(token, uuid, username);

        notifyPlayer(Component.literal("Linked as " + username + "! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(WebsiteConfig.SYNC_ORDERS_TOGGLE
                                ? "Your orders will sync whenever you open the Manage Orders menu."
                                : "Turn on Website Sync in the settings to start syncing your orders.")
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static void handleFailure(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;

        if (cause instanceof LinkFailure failure) {
            notifyPlayer(error(failure.getMessage()));

            return;
        }

        notifyPlayer(error("Could not reach the Bazaar Utils website. Check your connection and try again."));
        Util.logError("Link confirm request failed", cause);
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

    private static Optional<String> string(Optional<JsonObject> body, String field) {
        return body.map(object -> object.get(field))
                .filter(element -> element != null && element.isJsonPrimitive())
                .map(JsonElement::getAsString)
                .filter(value -> !value.isBlank());
    }

    private static Component info(String message) {
        return Component.literal(message).withStyle(ChatFormatting.GRAY);
    }

    private static Component error(String message) {
        return Component.literal(message).withStyle(ChatFormatting.RED);
    }

    /**
     * Chat has to be written from the client thread, and these callbacks land on an HTTP worker.
     */
    private static void notifyPlayer(Component message) {
        Minecraft.getInstance().execute(() -> PlayerActionUtil.notifyAll(message));
    }

    /** Carries an already player-readable explanation out of the async chain. */
    private static final class LinkFailure extends RuntimeException {
        private LinkFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
