package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Lifecycle state of a tracked Bazaar order.
 *
 * <pre>
 *   Set ──► Partial ──► Filled  ──► Claimed   (terminal)
 *    │         │
 *    └─────────┴──────────────────► Expired
 *    │         │                       │
 *    └─────────┴───────────────────────┴──► Cancelled  (terminal)
 * </pre>
 *
 * <p>Only {@link Claimed} and {@link Cancelled} are terminal. {@link Filled} and
 * {@link Expired} remain live — both still require player action before the order
 * leaves the Orders screen.
 */
public sealed interface OrderStatus permits
        OrderStatus.Set,
        OrderStatus.Partial,
        OrderStatus.Filled,
        OrderStatus.Expired,
        OrderStatus.Cancelled,
        OrderStatus.Claimed {
    Codec<OrderStatus> CODEC = Codec.STRING.dispatch(
            "type",
            state -> switch (state) {
                case Set ignored -> "set";
                case Partial ignored -> "partial";
                case Filled ignored -> "filled";
                case Expired ignored -> "expired";
                case Cancelled ignored -> "cancelled";
                case Claimed ignored -> "claimed";
            },
            type -> switch (type) {
                case "set" -> Set.CODEC;
                case "partial" -> Partial.CODEC;
                case "filled" -> Filled.CODEC;
                case "expired" -> Expired.CODEC;
                case "cancelled" -> Cancelled.CODEC;
                case "claimed" -> Claimed.CODEC;
                default -> throw new IllegalArgumentException("Unknown OrderStatus: " + type);
            }
    );

    /**
     * Placed, no fills known of/handled yet.
     */
    record Set() implements OrderStatus {
        static final MapCodec<Set> CODEC = MapCodec.unit(new Set());
    }

    /**
     * At least one fill received; not yet complete.
     *
     * <p>{@code firstFilledAt} records when the first fill arrived. It is preserved
     * across subsequent partial fills by {@link Order#withFill}, so callers can always
     * ask "how long has this been partially filling?" even after many fill events.
     * Claiming filled volume does NOT advance this phase — the order stays live.
     */
    record Partial(long firstFilledAt, long lastFilledAt) implements OrderStatus {
        static final MapCodec<Partial> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.LONG.fieldOf("firstFilledAt").forGetter(Partial::firstFilledAt),
                        Codec.LONG.fieldOf("lastFilledAt").forGetter(Partial::lastFilledAt)
                ).apply(instance, Partial::new));
    }

    /**
     * Fully filled. {@code Order.filledAmount >= Order.originalAmount} implied.
     * Advances to {@link Claimed} once {@code claimedAmount >= originalAmount}.
     */
    record Filled(long filledAt) implements OrderStatus {
        static final MapCodec<Filled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("filledAt").forGetter(Filled::filledAt))
                        .apply(instance, Filled::new));
    }

    /**
     * The order has lapsed. Hypixel removed its unfilled volume from the market book
     * automatically. The representational slot remains on the Orders page so the player
     * can claim filled-but-unclaimed volume and then cancel via Options to recover the
     * escrow (coins / items) for the unfilled remainder.
     */
    record Expired(long expiredAt) implements OrderStatus {
        static final MapCodec<Expired> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("expiredAt").forGetter(Expired::expiredAt))
                        .apply(instance, Expired::new));
    }

    /**
     * Terminal. Only reachable from {@link Set} or {@link Partial}.
     * Precondition: {@code claimedAmount >= filledAmount} at cancel time.
     */
    record Cancelled(long cancelledAt) implements OrderStatus {
        static final MapCodec<Cancelled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("cancelledAt").forGetter(Cancelled::cancelledAt))
                        .apply(instance, Cancelled::new));
    }

    /**
     * Terminal. Only reachable from {@link Filled}.
     * {@code Order.filledAmount >= Order.originalAmount} implied.
     */
    record Claimed(long claimedAt) implements OrderStatus {
        static final MapCodec<Claimed> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("claimedAt").forGetter(Claimed::claimedAt))
                        .apply(instance, Claimed::new));
    }

    // ── Display ───────────────────────────────────────────────────────────────

    default String describe() {
        return switch (this) {
            case Set ignored -> "Waiting for fills...";
            case Partial partial -> "Partially filled since " + partial.firstFilledAt() + ", last fill @ " + partial.lastFilledAt();
            case Filled filled -> "Complete — filled @ " + filled.filledAt();
            case Expired expired -> "Expired @ " + expired.expiredAt();
            case Claimed claimed -> "Complete — claimed @ " + claimed.claimedAt();
            case Cancelled cancelled -> "Complete — cancelled @ " + cancelled.cancelledAt();
        };
    }
}