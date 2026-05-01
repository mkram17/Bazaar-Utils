package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Who placed a tracked order, and — a separate question — whether the order's
 * profile is shared with anyone else who could still act on it since.
 */
public sealed interface OrderAttribution permits
        OrderAttribution.Self,
        OrderAttribution.SelfInCoop,
        OrderAttribution.CoopPeer,
        OrderAttribution.CoopUnknown {

    Codec<OrderAttribution> CODEC = Codec.STRING.partialDispatch(
            "type",
            attribution -> DataResult.success(switch (attribution) {
                case Self ignored -> "self";
                case SelfInCoop ignored -> "self_in_coop";
                case CoopPeer ignored -> "coop_peer";
                case CoopUnknown ignored -> "coop_unknown";
            }),
            type -> switch (type) {
                case "self" -> DataResult.success(Self.CODEC);
                case "self_in_coop" -> DataResult.success(SelfInCoop.CODEC);
                case "coop_peer" -> DataResult.success(CoopPeer.CODEC);
                case "coop_unknown" -> DataResult.success(CoopUnknown.CODEC);
                default -> DataResult.error(() -> "Unknown OrderAttribution: " + type);
            }
    );

    /** Placed by the local player, on a profile not known to be coop. No one else can act on it. */
    record Self() implements OrderAttribution {
        public static final Self INSTANCE = new Self();
        static final MapCodec<Self> CODEC = MapCodec.unit(INSTANCE);
    }

    /**
     * Placed by the local player, on a profile known to be coop. Distinct from
     * {@link Self} because {@link #isCoopContext()} must still return {@code true}
     * for it — the order stays exposed to a peer's claim or cancel despite being
     * self-placed.
     */
    record SelfInCoop() implements OrderAttribution {
        public static final SelfInCoop INSTANCE = new SelfInCoop();
        static final MapCodec<SelfInCoop> CODEC = MapCodec.unit(INSTANCE);
    }

    /** Placed by {@code username}, a confirmed coop peer other than the local player. */
    record CoopPeer(@NotNull String username) implements OrderAttribution {
        static final MapCodec<CoopPeer> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.STRING.fieldOf("username").forGetter(CoopPeer::username))
                        .apply(instance, CoopPeer::new));

        public CoopPeer {
            Objects.requireNonNull(username, "username");
            if (username.isBlank()) {
                throw new IllegalArgumentException("CoopPeer username must not be blank");
            }
        }
    }

    /** Coop attribution with no confirmed peer identity. */
    record CoopUnknown() implements OrderAttribution {
        public static final CoopUnknown INSTANCE = new CoopUnknown();
        static final MapCodec<CoopUnknown> CODEC = MapCodec.unit(INSTANCE);
    }

    /**
     * {@code true} for every state but {@link Self} — whether this order's profile
     * could have had someone other than its placer act on it since, independent of
     * who actually placed it.
     */
    default boolean isCoopContext() {
        return !(this instanceof Self);
    }

    /** {@code true} only for a confirmed peer order — {@link CoopPeer} specifically. Narrower than {@link #isCoopContext()}. */
    default boolean isConfirmedPeer() {
        return this instanceof CoopPeer;
    }

    default String describe() {
        return switch (this) {
            case Self ignored -> "attrib=self";
            case SelfInCoop ignored -> "attrib=coop[self]";
            case CoopPeer(String username) -> "attrib=coop[peer=" + username + "]";
            case CoopUnknown ignored -> "attrib=coop[peer=(unconfirmed)]";
        };
    }

    /**
     * Resolves attribution from a parsed "By:" lore line. Because Hypixel only renders
     * "By:" on co-op profiles, any non-blank {@code byUsername} implies a co-op context —
     * returning {@link SelfInCoop} when it matches {@code localPlayerName} and
     * {@link CoopPeer} otherwise. When {@code byUsername} is absent or blank, falls back
     * to {@code isKnownCoop}.
     */
    static @NotNull OrderAttribution fromByLine(@Nullable String byUsername, @NotNull String localPlayerName, boolean isKnownCoop) {
        if (byUsername != null && !byUsername.isBlank()) {
            return byUsername.equalsIgnoreCase(localPlayerName)
                    ? SelfInCoop.INSTANCE
                    : new CoopPeer(byUsername);
        }

        return isKnownCoop ? SelfInCoop.INSTANCE : Self.INSTANCE;
    }
}
