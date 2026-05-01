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

        /**
         * The shape {@code newTotal} filled units represents, given this shape is the
         * current one and {@code currentTotal} is how many units it already reflects.
         * Never regresses: {@code newTotal <= currentTotal} returns {@code this}
         * unchanged, the SAME reference, so a caller can rely on reference-identity to
         * detect "nothing genuinely changed." Preserves history where the target shape
         * matches this one — {@link Partial#firstFilledAt()} carries forward across
         * every subsequent partial fill; a target of {@link Filled} that's already
         * {@link Filled} keeps its OWN {@code filledAt} rather than restamping, so the
         * first moment full completion was observed is never overwritten by a later one.
         *
         * <p>The single canonical fill-advancement implementation — both a confirmed
         * delta ({@link Order#withFill}) and a screen/chat reconciliation
         * ({@link OrderStatus#reconciledWith}) build on this rather than each
         * reimplementing the same switch independently.
         */
        default FillState advancedTo(int currentTotal, int newTotal, int volume, long timestamp) {
            if (newTotal <= currentTotal) return this;

            return newTotal >= volume
                    ? new Filled(timestamp)
                    : (this instanceof Partial existing
                        ? new Partial(existing.firstFilledAt(), timestamp)
                        : new Partial(timestamp, timestamp));
        }

        /** Constructs the shape {@code filledAmount} (out of {@code volume}) implies, with no prior state to advance from — the synthesis-time case. */
        static FillState of(int filledAmount, int volume, long observedAt) {
            if (filledAmount >= volume) return new Filled(observedAt);
            if (filledAmount > 0) return new Partial(observedAt, observedAt);

            return new Set();
        }

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

    /**
     * Reconciles this status against a freshly observed fill total and expiry flag —
     * the one place a screen or chat observation advances a status, whether or not
     * it's currently wrapped in {@link Expired}. Advances the wrapped {@link FillState}
     * via {@link FillState#advancedTo}, then wraps (or keeps wrapped) in {@link Expired}
     * exactly when {@code nowExpired} says so — preserving an already-known
     * {@code expiredAt} rather than restamping it, and reusing THIS SAME {@link Expired}
     * instance outright when the observation implies no genuine change at all.
     *
     * @param priorFilledAmount the filledAmount this status's own fill state already reflects
     * @param reconciledFill    the (already floor/ceiling-clamped) fill total to advance to
     * @param volume            the order's {@code originalAmount}
     * @param nowExpired        whether THIS observation shows the order as expired
     * @param observedAt        this observation's own timestamp
     * @throws IllegalStateException if this status is already {@link Terminal} — a
     *         reconciliation reaching here on a terminal order is a caller bug.
     */
    default OrderStatus reconciledWith(int priorFilledAmount, int reconciledFill, int volume, boolean nowExpired, long observedAt) throws IllegalStateException {
        var prior = fillState().orElseThrow(() -> new IllegalStateException("Cannot reconcile a terminal order's fill state: " + describe()));
        FillState advanced = prior.advancedTo(priorFilledAmount, reconciledFill, volume, observedAt);

        if (!nowExpired) return (OrderStatus) advanced;

        var priorExpired = this instanceof Expired existing ? existing : null;
        if (priorExpired != null && priorExpired.interrupted().equals(advanced)) return priorExpired;

        long expiredAt = priorExpired != null ? priorExpired.expiredAt() : observedAt;

        return new Expired(expiredAt, advanced);
    }

    /** Constructs the status a first-ever observation implies, with no prior order to reconcile against — the synthesis-time case. */
    static OrderStatus observed(int filledAmount, int volume, boolean expired, long observedAt) {
        var fillState = FillState.of(filledAmount, volume, observedAt);

        return expired ? new Expired(observedAt, fillState) : (OrderStatus) fillState;
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