package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public sealed interface OrderStatus permits
        OrderStatus.Set,
        OrderStatus.Partial,
        OrderStatus.Filled,
        OrderStatus.Cancelled,
        OrderStatus.Claimed {
    /**
     * Placed, no fills known of/handled yet.
     */
    record Set() implements OrderStatus {
        static final MapCodec<Set> CODEC = MapCodec.unit(new Set());
    }

    /**
     * At least one fill received; not yet complete.
     * Claiming filled volume does NOT advance this phase — order stays live.
     */
    record Partial() implements OrderStatus {
        static final MapCodec<Partial> CODEC = MapCodec.unit(new Partial());
    }

    /**
     * Fully filled. Order.filledAmount >= Order.originalAmount implied.
     * Advances to Claimed once claimedAmount >= originalAmount.
     */
    record Filled(long filledAt) implements OrderStatus {
        static final MapCodec<Filled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("filledAt").forGetter(Filled::filledAt)
        ).apply(instance, Filled::new));
    }


    /**
     * Terminal. Only reachable from Filled.
     * Order.filledAmount >= Order.originalAmount implied.
     */
    record Claimed(long claimedAt) implements OrderStatus {
        static final MapCodec<Claimed> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("claimedAt").forGetter(Claimed::claimedAt)
        ).apply(instance, Claimed::new));
    }

    /**
     * Terminal. Reachable from Set or Partial only.
     * Precondition: claimedAmount >= filledAmount at cancel time.
     */
    record Cancelled(long cancelledAt) implements OrderStatus {
        static final MapCodec<Cancelled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("confirmedAt").forGetter(Cancelled::cancelledAt)
        ).apply(instance, Cancelled::new));
    }

    Codec<OrderStatus> CODEC = Codec.STRING.dispatch(
            "type",
            state -> switch (state) {
                case Set ignored -> "set";
                case Partial ignored -> "partial";
                case Filled ignored -> "filled";
                case Cancelled ignored -> "cancelled";
                case Claimed ignored -> "claimed";
            },
            type -> switch (type) {
                case "set" -> Set.CODEC;
                case "partial" -> Partial.CODEC;
                case "filled" -> Filled.CODEC;
                case "cancelled" -> Cancelled.CODEC;
                case "claimed" -> Claimed.CODEC;
                default -> throw new IllegalArgumentException("Unknown OrderStatus: " + type);
            }
    );
}