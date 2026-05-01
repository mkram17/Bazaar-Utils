package com.github.mkram17.bazaarutils.utils.bazaar.market.order;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Lifecycle state of a tracked Bazaar order.
 *
 * <pre>
 *   Set ──► Partial ──► Filled  ──► Claimed   (terminal)
 *    │         │
 *    └─────────┴──────────────────► Expired(previousStatus) ──► Claimed / Cancelled (terminal)
 *    │         │
 *    └─────────┴───────────────────────────► Cancelled  (terminal)
 * </pre>
 *
 * <p>Only {@link Claimed} and {@link Cancelled} are terminal. {@link Expired} is
 * deliberately NOT terminal, and not a peer of {@link Set}/{@link Partial}/{@link Filled}
 * either — it's a WRAPPER: Hypixel withdraws an order's market position after its
 * 7-day lifetime elapses regardless of what state that position was in, so
 * {@code Expired} carries whichever of {@link Set}, {@link Partial}, or
 * {@link Filled} the order was actually in at that moment, as {@code previousStatus}.
 * The actions still available on an expired order are exactly what that embedded
 * status already implied — minus the possibility of further fill, which withdrawal
 * rules out unconditionally. See {@link Order#isFilled()}/{@link Order#isCancellable()}
 * for where this actually gets consulted.
 */
public sealed interface OrderStatus permits
        OrderStatus.NonTerminal,
        OrderStatus.Expired,
        OrderStatus.Cancelled,
        OrderStatus.Claimed {

    Codec<OrderStatus> CODEC = Codec.STRING.dispatch(
            "type",
            status -> switch (status) {
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
     * The three statuses an order can hold while its market position is genuinely
     * open — unfilled, partially filled, or fully filled. This is exactly the set
     * {@link Expired} can wrap: an order can only expire OUT of one of these three,
     * never out of a terminal state.
     */
    sealed interface NonTerminal extends OrderStatus permits Set, Partial, Filled {
        Codec<NonTerminal> CODEC = Codec.STRING.dispatch(
                "type",
                status -> switch (status) {
                    case Set ignored -> "set";
                    case Partial ignored -> "partial";
                    case Filled ignored -> "filled";
                },
                type -> switch (type) {
                    case "set" -> Set.CODEC;
                    case "partial" -> Partial.CODEC;
                    case "filled" -> Filled.CODEC;
                    default -> throw new IllegalArgumentException("Unknown NonTerminal OrderStatus: " + type);
                }
        );
    }

    /** Order is placed; no fill has been processed yet. */
    record Set() implements NonTerminal {
        static final MapCodec<Set> CODEC = MapCodec.unit(new Set());
    }

    /**
     * At least one fill received, not yet complete. {@code firstFilledAt} is set on the
     * first fill and carried forward unchanged through every subsequent partial fill, so
     * it always answers "how long has this been filling?" regardless of how many fills
     * have landed since. Claiming already-filled volume does not advance this state.
     */
    record Partial(long firstFilledAt, long lastFilledAt) implements NonTerminal {
        static final MapCodec<Partial> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.LONG.fieldOf("firstFilledAt").forGetter(Partial::firstFilledAt),
                        Codec.LONG.fieldOf("lastFilledAt").forGetter(Partial::lastFilledAt)
                ).apply(instance, Partial::new));
    }

    /**
     * Every unit filled — {@code filledAmount >= originalAmount} is implied. Advances to
     * {@link Claimed} once the filled volume is fully claimed.
     */
    record Filled(long filledAt) implements NonTerminal {
        static final MapCodec<Filled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("filledAt").forGetter(Filled::filledAt))
                        .apply(instance, Filled::new));
    }

    /**
     * The order's market position was withdrawn by Hypixel after its 7-day lifetime
     * elapsed — regardless of what {@code previousStatus} it was in at that moment.
     * The order stays live (see {@link Order#isLive()}): it still requires player
     * action before leaving the Orders screen, and exactly which action is still
     * governed by {@code previousStatus} — see {@link Order#effectiveNonTerminal()}.
     * Detected either by a periodic check against the order's own expiry stamp, or
     * by observing the expired lore token on the Orders screen.
     */
    record Expired(long expiredAt, NonTerminal previousStatus) implements OrderStatus {
        static final MapCodec<Expired> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.LONG.fieldOf("expiredAt").forGetter(Expired::expiredAt),
                        NonTerminal.CODEC.fieldOf("previousStatus").forGetter(Expired::previousStatus)
                ).apply(instance, Expired::new));
    }

    /** Terminal. Reachable from {@link Set}, {@link Partial}, or an {@link Expired} wrapping either. */
    record Cancelled(long cancelledAt) implements OrderStatus {
        static final MapCodec<Cancelled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("cancelledAt").forGetter(Cancelled::cancelledAt))
                        .apply(instance, Cancelled::new));
    }

    /** Terminal. Reachable from {@link Filled} or an {@link Expired} wrapping it. */
    record Claimed(long claimedAt) implements OrderStatus {
        static final MapCodec<Claimed> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("claimedAt").forGetter(Claimed::claimedAt))
                        .apply(instance, Claimed::new));
    }

    /**
     * The {@link NonTerminal} status this order's fill/claim semantics are currently
     * governed by — itself, when already {@link NonTerminal}; the wrapped
     * {@code previousStatus}, when this is {@link Expired}. Empty for {@link Cancelled}/
     * {@link Claimed}, which are fully resolved and carry no such fallback. The single
     * place "look through an Expired wrapper" is implemented — every predicate and
     * transition elsewhere that needs to see past expiry to the position it interrupted
     * goes through this.
     */
    default Optional<NonTerminal> effectiveNonTerminal() {
        return switch (this) {
            case NonTerminal nonTerminal -> Optional.of(nonTerminal);
            case Expired(var ignored, var previous) -> Optional.of(previous);
            case Cancelled ignored -> Optional.empty();
            case Claimed ignored -> Optional.empty();
        };
    }

    default String describe() {
        return switch (this) {
            case Set ignored -> "Waiting for fills...";
            case Partial partial -> "Partially filled since " + partial.firstFilledAt() + ", last fill @ " + partial.lastFilledAt();
            case Filled filled -> "Complete — filled @ " + filled.filledAt();
            case Expired(var expiredAt, var previous) -> "Expired @ " + expiredAt + " (" + previous.describe() + ")";
            case Claimed claimed -> "Complete — claimed @ " + claimed.claimedAt();
            case Cancelled cancelled -> "Complete — cancelled @ " + cancelled.cancelledAt();
        };
    }
}