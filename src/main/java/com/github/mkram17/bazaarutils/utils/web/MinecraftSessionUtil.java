package com.github.mkram17.bazaarutils.utils.web;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The client half of Mojang's session handshake — the same one a vanilla client performs when it
 * joins a server.
 *
 * <p>This is what makes linking an <em>authentication</em> rather than a claim. Resolving a name
 * to a UUID through Mojang's profile API would prove nothing: that endpoint is a public directory
 * and will happily resolve anyone's name for anyone. Instead the mod calls {@code joinServer} with
 * a nonce the website issued (the link code itself), Mojang records that against this session, and
 * the website then asks {@code hasJoined} <em>which account</em> did it. The identity comes back
 * from Mojang, so a UUID sent by the client is never needed and never trusted.</p>
 *
 * <p>A session that cannot complete the handshake — an offline/cracked launch, or a dev environment
 * without credentials — cannot link. That is deliberate; the alternative is linking blind.</p>
 */
public final class MinecraftSessionUtil {
    /** Two different exceptions mean the same thing to a player: log in again. */
    private static final String STALE_SESSION = "Your Minecraft session is not valid. Restart the game and log in again.";

    private MinecraftSessionUtil() {}

    /** The identity of the currently logged-in client. */
    public record Session(UUID profileId, String username) {
        /** Dashless lowercase, the form the website stores and the form {@code hasJoined} returns. */
        public String dashlessUuid() {
            return dashless(profileId);
        }
    }

    public static Optional<Session> currentSession() {
        User user = Minecraft.getInstance().getUser();

        if (user == null) return Optional.empty();

        UUID profileId = user.getProfileId();
        String username = user.getName();

        if (profileId == null || username == null || username.isBlank()) return Optional.empty();

        return Optional.of(new Session(profileId, username));
    }

    /**
     * Tells Mojang that this session is joining {@code serverId}.
     *
     * <p>Blocking network call — never invoke this on the render thread. The record Mojang keeps is
     * short-lived, so the request that asks {@code hasJoined} about it must follow promptly.</p>
     *
     * <p>Note for anyone porting: the accessor moved. On 1.21.11 the session service hangs off
     * {@code Minecraft.services()}, not the {@code Minecraft.getMinecraftSessionService()} of
     * earlier versions.</p>
     *
     * @param serverId nonce issued by the website; must match byte-for-byte what the website will
     *                 pass to {@code hasJoined}
     */
    public static void joinServer(String serverId) throws AuthenticationException {
        Minecraft client = Minecraft.getInstance();
        User user = client.getUser();

        if (user == null) {
            throw new AuthenticationException("No Minecraft session is active.");
        }

        client.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
    }

    /**
     * A player-facing explanation of a failed handshake. The distinction that matters most is
     * "Mojang is down, try later" versus "this session can never do this".
     */
    public static String describeFailure(AuthenticationException exception) {
        return switch (exception) {
            case AuthenticationUnavailableException ignored ->
                    "Mojang's session servers are unreachable. Try again in a few minutes.";
            case InvalidCredentialsException ignored -> STALE_SESSION;
            case ForcedUsernameChangeException ignored -> STALE_SESSION;
            case InsufficientPrivilegesException ignored ->
                    "This account is not allowed to use multiplayer, so it cannot be verified.";
            case UserBannedException ignored ->
                    "This account is banned from multiplayer, so it cannot be verified.";
            default -> "Could not verify your Minecraft session. Linking requires a genuine (non-offline) login.";
        };
    }

    /** Strips dashes and lowercases, matching the website's stored form. */
    public static String dashless(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }
}
