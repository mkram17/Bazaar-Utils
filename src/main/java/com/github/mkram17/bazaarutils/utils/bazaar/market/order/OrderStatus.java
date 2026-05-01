package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Lifecycle state of a tracked Bazaar order, split along two independent questions:
 * whether some action — a claim, a cancellation, or the market still filling it — could
 * still happen to the order ({@link Actionable}), and whether nothing further will ever
 * happen to it ({@link Terminal}).
 *
 * <p>{@link Actionable} splits again into {@link BookState} — a position genuinely
 * resting in the live book — and {@link Settled}, which has left the book but may still
 * need a claim or, for {@link Expired}, a cancellation.
 */
public sealed interface OrderStatus permits
        OrderStatus.Actionable,
        OrderStatus.Terminal {

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
     * Not yet {@link Terminal} — cancellation and claiming both remain possible actions,
     * even for an order that has {@link Expired} from the market.
     */
    sealed interface Actionable extends OrderStatus permits BookState, Settled {}

    /**
     * How far an order's fill has progressed — {@link Set} or {@link Partial} while
     * still resting in the book, {@link Filled} once fully matched — kept as its own
     * type rather than folded into {@link BookState} because {@link Expired} needs to
     * freeze exactly one of these three shapes, {@link Filled} included: Hypixel
     * withdraws a lapsed position after 7 days regardless of how much had filled, so an
     * already-complete order can still expire. Typing {@code Expired.interrupted} as
     * {@code FillState} rather than the broader {@link Actionable} also rules out an
     * {@code Expired} wrapping another {@code Expired} at the type level — {@code Expired}
     * itself is not a {@code FillState}.
     */
    sealed interface FillState permits Set, Partial, Filled {
        Codec<FillState> CODEC = Codec.STRING.dispatch(
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
                    default -> throw new IllegalArgumentException("Unknown FillState OrderStatus: " + type);
                }
        );

        /** {@code true} for {@link Set} and {@link Partial}, {@code false} for {@link Filled}. */
        boolean hasUnfilledRemainder();

        default String describeFill() {
            return switch (this) {
                case Set ignored -> "Waiting for fills...";
                case Partial partial -> "Partially filled since " + partial.firstFilledAt() + ", last fill @ " + partial.lastFilledAt();
                case Filled filled -> "Complete — filled @ " + filled.filledAt();
            };
        }
    }

    /** A position genuinely resting in Hypixel's live order book right now — {@link Filled} has already left it. */
    sealed interface BookState extends Actionable permits Set, Partial {}

    /** Placed; no fill has been processed yet. */
    record Set() implements BookState, FillState {
        static final MapCodec<Set> CODEC = MapCodec.unit(new Set());

        @Override
        public boolean hasUnfilledRemainder() {
            return true;
        }
    }

    /**
     * At least one fill received, not yet complete. {@code firstFilledAt} is set
     * on the first fill and carried forward unchanged through every subsequent
     * partial fill, so it always answers "how long has this been filling?"
     * regardless of how many fills have landed since.
     */
    record Partial(long firstFilledAt, long lastFilledAt) implements BookState, FillState {
        static final MapCodec<Partial> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.LONG.fieldOf("firstFilledAt").forGetter(Partial::firstFilledAt),
                        Codec.LONG.fieldOf("lastFilledAt").forGetter(Partial::lastFilledAt)
                ).apply(instance, Partial::new));

        @Override
        public boolean hasUnfilledRemainder() {
            return true;
        }
    }

    /**
     * Left the live book — by completing ({@link Filled}) or lapsing ({@link Expired})
     * — but not necessarily {@link Terminal} yet: a claim may still be owed, and an
     * {@link Expired} order may still need cancelling to recover its unfilled escrow.
     */
    sealed interface Settled extends Actionable permits Filled, Expired {}

    /** Every unit filled. */
    record Filled(long filledAt) implements Settled, FillState {
        static final MapCodec<Filled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("filledAt").forGetter(Filled::filledAt))
                        .apply(instance, Filled::new));

        @Override
        public boolean hasUnfilledRemainder() {
            return false;
        }
    }

    /**
     * The order lapsed its Hypixel lifetime and lost its live book position.
     * {@code interrupted} preserves exactly which {@link FillState} it was in at that
     * moment — {@link Filled} included, since Hypixel withdraws a lapsed position
     * after 7 days regardless of fill state. The player must claim any filled volume,
     * then cancel via Options, to recover the escrow held for whatever never filled.
     */
    record Expired(long expiredAt, FillState interrupted) implements Settled {
        static final MapCodec<Expired> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.LONG.fieldOf("expiredAt").forGetter(Expired::expiredAt),
                        FillState.CODEC.fieldOf("interrupted").forGetter(Expired::interrupted)
                ).apply(instance, Expired::new));
    }

    /** Terminal — {@link Cancelled} or {@link Claimed}. Nothing further ever happens to the order. */
    sealed interface Terminal extends OrderStatus permits Cancelled, Claimed {}

    /** The order was cancelled, its unfilled remainder returned. */
    record Cancelled(long cancelledAt) implements Terminal {
        static final MapCodec<Cancelled> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("cancelledAt").forGetter(Cancelled::cancelledAt))
                        .apply(instance, Cancelled::new));
    }

    /** Every filled unit was claimed. */
    record Claimed(long claimedAt) implements Terminal {
        static final MapCodec<Claimed> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.LONG.fieldOf("claimedAt").forGetter(Claimed::claimedAt))
                        .apply(instance, Claimed::new));
    }

    /**
     * The {@link FillState} this status is actually in, or was in before expiring —
     * present for {@link Set}, {@link Partial}, and {@link Filled} directly, and for
     * {@link Expired} via its {@code interrupted} value; empty only for {@link Terminal}.
     */
    default Optional<FillState> fillState() {
        return switch (this) {
            case Set set -> Optional.of(set);
            case Partial partial -> Optional.of(partial);
            case Filled filled -> Optional.of(filled);
            case Expired(var ignoredAt, var interrupted) -> Optional.of(interrupted);
            case Terminal ignored -> Optional.empty();
        };
    }

    /** {@code true} when every unit has matched — bare {@link Filled}, or an {@link Expired} that interrupted one. */
    default boolean isFilled() {
        return fillState().filter(it -> it instanceof Filled).isPresent();
    }

    /** {@code true} when this order still has volume that never filled. {@code false} once fully matched or {@link Terminal}. */
    default boolean hasUnfilledRemainder() {
        return fillState()
                .map(FillState::hasUnfilledRemainder)
                .orElse(false);
    }

    default String describe() {
        return switch (this) {
            case Set set -> set.describeFill();
            case Partial partial -> partial.describeFill();
            case Filled filled -> filled.describeFill();
            case Expired(var expiredAt, var interrupted) -> "Expired @ " + expiredAt + " — interrupted: " + interrupted.describeFill();
            case Claimed claimed -> "Complete — claimed @ " + claimed.claimedAt();
            case Cancelled cancelled -> "Complete — cancelled @ " + cancelled.cancelledAt();
        };
    }
}