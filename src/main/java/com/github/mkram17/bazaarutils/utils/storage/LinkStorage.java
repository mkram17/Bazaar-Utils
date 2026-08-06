package com.github.mkram17.bazaarutils.utils.storage;

import java.util.Optional;
import java.util.UUID;

import com.github.mkram17.bazaarutils.utils.web.MinecraftSessionUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
    /** How much of a token {@link LinkData#tokenPrefix} keeps. */
    private static final int TOKEN_PREFIX_LENGTH = 8;

    private LinkStorage() {}

    /**
     * @param token       bearer token, shown by the website exactly once at link time
     * @param tokenPrefix the token's first characters — not a secret, it exists so a user can tell
     *                    installs apart
     * @param uuid        dashless lowercase UUID of the account this token was issued for
     * @param username    display only, and refreshed on link; players rename, so this is never a key
     */
    public record LinkData(String token, String tokenPrefix, String uuid, String username) {
        /** What {@link #clear()} leaves behind, and what a missing file loads as. */
        public static final LinkData EMPTY = new LinkData("", "", "", "");

        public static final Codec<LinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("token", "").forGetter(LinkData::token),
                Codec.STRING.optionalFieldOf("tokenPrefix", "").forGetter(LinkData::tokenPrefix),
                Codec.STRING.optionalFieldOf("uuid", "").forGetter(LinkData::uuid),
                Codec.STRING.optionalFieldOf("username", "").forGetter(LinkData::username)
        ).apply(instance, LinkData::new));

        // Every field is optional on disk and nullable at the call sites that build one, but
        // Codec.STRING cannot encode a null — so absent has to mean blank, not null.
        public LinkData {
            token = orBlank(token);
            tokenPrefix = orBlank(tokenPrefix);
            uuid = orBlank(uuid);
            username = orBlank(username);
        }

        private static String orBlank(String value) {
            return value == null ? "" : value;
        }
    }

    public static final DataStorage<LinkData> INSTANCE = new DataStorage<>(() -> LinkData.EMPTY, "website_link", LinkData.CODEC);

    /**
     * The stored link, or empty when there is nothing usable to read.
     *
     * <p>Every accessor below goes through this. A half-written file, or the empty data
     * {@link #clear()} leaves behind, must read as "not linked" rather than as a link with null
     * fields that each caller has to check for itself.</p>
     */
    private static Optional<LinkData> linked() {
        LinkData data = INSTANCE.get();

        return isSet(data.token()) && isSet(data.uuid()) ? Optional.of(data) : Optional.empty();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    public static boolean isLinked() {
        return linked().isPresent();
    }

    /**
     * The token for {@code profileId}, or empty when nothing is linked or the stored link belongs
     * to a different Minecraft account. This is the account-switch guard — callers that push data
     * must go through it rather than reading {@link #INSTANCE} directly.
     */
    public static Optional<String> tokenFor(UUID profileId) {
        return linked()
                .filter(data -> data.uuid().equals(MinecraftSessionUtil.dashless(profileId)))
                .map(LinkData::token);
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
        return linked().filter(data -> data.token().equals(token)).isPresent();
    }

    // Both of these are display-only, and a link can carry neither. Filtering keeps "stored but
    // blank" reading as absent, the way a null field did before these became codec-backed.
    public static Optional<String> linkedUsername() {
        return linked().map(LinkData::username).filter(LinkStorage::isSet);
    }

    public static Optional<String> tokenPrefix() {
        return linked().map(LinkData::tokenPrefix).filter(LinkStorage::isSet);
    }

    public static void store(String token, String uuid, String username) {
        INSTANCE.set(new LinkData(
                token,
                token.substring(0, Math.min(TOKEN_PREFIX_LENGTH, token.length())),
                normalizeUuid(uuid),
                username
        ));

        persistNow();
    }

    /**
     * Forces the stored UUID into the dashless lowercase form {@link #tokenFor} compares against.
     *
     * <p>The value comes off the wire, and the wire format does not pin its casing or dashes. An
     * unnormalized one is not a visible error anywhere: the link saves, {@code /bu link} reports
     * it as connected, and every sync afterwards is dropped by the account-switch guard because
     * the equality never holds.</p>
     */
    private static String normalizeUuid(String uuid) {
        return uuid == null ? null : MinecraftSessionUtil.dashless(uuid);
    }

    public static void clear() {
        INSTANCE.set(LinkData.EMPTY);
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
        INSTANCE.flush();
    }
}
