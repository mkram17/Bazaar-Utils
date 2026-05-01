package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.OptionalInt;

/**
 * The known screen position of a tracked Bazaar order. Three states:
 *
 * <ul>
 *   <li>{@link OnScreen}   — rendered at a concrete screen slot index.</li>
 *   <li>{@link OffScreen}  — present in the queue but beyond the visible window;
 *       logical position is known, order is not rendered.</li>
 * </ul>
 */
public sealed interface OrderSlotPosition permits
        OrderSlotPosition.OnScreen,
        OrderSlotPosition.OffScreen {

    Codec<OrderSlotPosition> CODEC = Codec.STRING.dispatch(
            "type",
            pos -> switch (pos) {
                case OnScreen ignored -> "on_screen";
                case OffScreen ignored -> "off_screen";
            },
            type -> switch (type) {
                case "on_screen" -> OnScreen.CODEC;
                case "off_screen" -> OffScreen.CODEC;
                default -> throw new IllegalArgumentException("Unknown OrderSlotPosition type: " + type);
            }
    );

    // ── Variants ─────────────────────────────────────────────────────────────

    /**
     * The order is rendered on the Orders page at screen slot {@code slot}.
     */
    record OnScreen(int slot) implements OrderSlotPosition {
        static final MapCodec<OnScreen> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("slot").forGetter(OnScreen::slot))
                        .apply(instance, OnScreen::new));
    }

    /**
     * The order is in the queue past the visible window.
     * {@code logicalPos} is its 0-based position within the side's full order list.
     */
    record OffScreen(int logicalPos) implements OrderSlotPosition {
        static final MapCodec<OffScreen> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("logicalPos").forGetter(OffScreen::logicalPos))
                        .apply(instance, OffScreen::new));
    }

    // ── Predicates ────────────────────────────────────────────────────────────

    /** {@code true} when the order is currently rendered on the Orders page. */
    default boolean isVisible() {
        return this instanceof OnScreen;
    }

    /**
     * {@code true} if this is an {@link OnScreen} position at exactly {@code slot}.
     * Avoids a pattern-match at every reconciliation call site.
     */
    default boolean isOnScreenAt(int slot) {
        return this instanceof OnScreen(int index) && index == slot;
    }

    default OptionalInt indexIfVisible() {
        return this instanceof OnScreen(int slot) ? OptionalInt.of(slot) : OptionalInt.empty();
    }

    // ── Display ───────────────────────────────────────────────────────────────

    default String describe() {
        return switch (this) {
            case OnScreen(int slot) -> "slot=" + slot;
            case OffScreen(int logicalPos) -> "off-screen@" + logicalPos;
        };
    }
}