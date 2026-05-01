package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Who placed a tracked order, and — a separate question — whether the order's
 * profile is shared with anyone else who could still act on it since.
 */
public sealed interface OrderAttribution permits
        OrderAttribution.Self,
        OrderAttribution.SelfInCoop,
        OrderAttribution.CoopPeer,
        OrderAttribution.CoopUnknown {

    Codec<OrderAttribution> CODEC = Codec.STRING.dispatch(
            "type",
            attribution -> switch (attribution) {
                case Self ignored -> "self";
                case SelfInCoop ignored -> "self_in_coop";
                case CoopPeer ignored -> "coop_peer";
                case CoopUnknown ignored -> "coop_unknown";
            },
            type -> switch (type) {
                case "self" -> Self.CODEC;
                case "self_in_coop" -> SelfInCoop.CODEC;
                case "coop_peer" -> CoopPeer.CODEC;
                case "coop_unknown" -> CoopUnknown.CODEC;
                default -> throw new IllegalArgumentException("Unknown OrderAttribution: " + type);
            }
    );

    /** Placed by the local player, on a profile not known to be coop. No one else can act on it. */
    record Self() implements OrderAttribution {
        static final MapCodec<Self> CODEC = MapCodec.unit(new Self());
    }

    /**
     * Placed by the local player, on a profile known to be coop. Distinct from
     * {@link Self} because {@link #isCoopContext()} must still return {@code true}
     * for it — the order stays exposed to a peer's claim or cancel despite being
     * self-placed.
     */
    record SelfInCoop() implements OrderAttribution {
        static final MapCodec<SelfInCoop> CODEC = MapCodec.unit(new SelfInCoop());
    }

    /** Placed by {@code username}, a confirmed coop peer other than the local player. */
    record CoopPeer(@NotNull String username) implements OrderAttribution {
        static final MapCodec<CoopPeer> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.STRING.fieldOf("username").forGetter(CoopPeer::username))
                        .apply(instance, CoopPeer::new));
    }

    /** Coop attribution with no confirmed peer identity. */
    record CoopUnknown() implements OrderAttribution {
        static final MapCodec<CoopUnknown> CODEC = MapCodec.unit(new CoopUnknown());
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
            case Self ignored -> "self";
            case SelfInCoop ignored -> "self (coop profile)";
            case CoopPeer(String username) -> "coop peer: " + username;
            case CoopUnknown ignored -> "coop peer (unconfirmed)";
        };
    }

    /**
     * Resolves attribution from a parsed "By:" lore line. When {@code byUsername} is
     * absent or matches {@code localPlayerName}, the result depends on
     * {@code isKnownCoop} alone — {@link SelfInCoop} if the profile is known coop,
     * {@link Self} otherwise — since neither input by itself distinguishes the two.
     */
    static @NotNull OrderAttribution fromByLine(@Nullable String byUsername, @NotNull String localPlayerName, boolean isKnownCoop) {
        if (byUsername != null && !byUsername.equalsIgnoreCase(localPlayerName)) {
            return new CoopPeer(byUsername);
        }

        return isKnownCoop ? new SelfInCoop() : new Self();
    }
}