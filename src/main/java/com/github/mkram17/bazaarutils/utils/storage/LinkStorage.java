package com.github.mkram17.bazaarutils.utils.storage;

import java.util.Optional;
import java.util.UUID;

import com.github.mkram17.bazaarutils.utils.web.MinecraftSessionUtil;

/**
 * The mod's half of the website link: one bearer token, plus the Minecraft account it belongs to.
 *
 * <p>Two things about this are worth stating plainly rather than discovering later.</p>
 *
 * <p><strong>The token is stored as plaintext JSON in the Minecraft config directory.</strong>
 * That is an acceptable trade for what this token can do — it is narrowly scoped, revocable from
 * the website, and push-only: it can upload order snapshots for one account and nothing else. It
 * grants no website session. Anyone with filesystem access can push orders as that account, so it
 * must never be widened beyond that.</p>
 *
 * <p><strong>The owning UUID is stored beside the token, and every read is filtered by it.</strong>
 * Players switch Minecraft accounts. Without this check, account B's orders would be pushed with
 * account A's token and land on account A's dashboard.</p>
 */
public final class LinkStorage {
    private LinkStorage() {}

    public static final class LinkData {
        /** Bearer token, shown by the website exactly once at link time. */
        public String token;

        /** First 8 characters of the token. Not a secret — it exists so a user can tell installs apart. */
        public String tokenPrefix;

        /** Dashless lowercase UUID of the account this token was issued for. */
        public String uuid;

        /** Display only, and refreshed on link. Players rename, so this is never a key. */
        public String username;
    }

    public static final DataStorage<LinkData> INSTANCE = new DataStorage<>(LinkData::new, "website_link", LinkData.class);

    public static boolean isLinked() {
        LinkData data = INSTANCE.get();

        return data.token != null && !data.token.isBlank() && data.uuid != null && !data.uuid.isBlank();
    }

    /**
     * The token for {@code profileId}, or empty when nothing is linked or the stored link belongs
     * to a different Minecraft account. This is the account-switch guard — callers that push data
     * must go through it rather than reading {@link #INSTANCE} directly.
     */
    public static Optional<String> tokenFor(UUID profileId) {
        if (!isLinked()) return Optional.empty();

        LinkData data = INSTANCE.get();

        return data.uuid.equals(MinecraftSessionUtil.dashless(profileId))
                ? Optional.of(data.token)
                : Optional.empty();
    }

    /**
     * Whether {@code token} is still the one this install holds.
     *
     * <p>The guard for responses that outlive the link they were sent under. A request carries its
     * token, not a reference to storage, so a rejection can land after the player has already
     * re-linked — and acting on it would clear a link that is perfectly good, with a chat message
     * saying the opposite.</p>
     */
    public static boolean isStoredToken(String token) {
        LinkData data = INSTANCE.get();

        return isLinked() && data.token.equals(token);
    }

    public static Optional<String> linkedUsername() {
        LinkData data = INSTANCE.get();

        return isLinked() ? Optional.ofNullable(data.username) : Optional.empty();
    }

    public static Optional<String> tokenPrefix() {
        LinkData data = INSTANCE.get();

        return isLinked() ? Optional.ofNullable(data.tokenPrefix) : Optional.empty();
    }

    public static void store(String token, String uuid, String username) {
        LinkData data = new LinkData();
        data.token = token;
        data.tokenPrefix = token.length() >= 8 ? token.substring(0, 8) : token;
        data.uuid = uuid;
        data.username = username;

        INSTANCE.set(data);
        persistNow();
    }

    public static void clear() {
        INSTANCE.set(new LinkData());
        persistNow();
    }

    /**
     * Writes immediately instead of waiting on {@link DataStorage}'s 5-second debounce. A token is
     * issued once and is unrecoverable afterwards, so a crash in that window would cost the user a
     * re-link.
     *
     * <p>Does file IO on the calling thread; call it off the render thread.</p>
     */
    private static void persistNow() {
        INSTANCE.save();
        DataStorage.flushAll();
    }
}
