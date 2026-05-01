package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI;

import java.util.OptionalInt;

/**
 * Where a tracked order currently sits relative to the Orders page's visible window: at
 * a concrete container slot ({@link OnScreen}), or beyond it with only a logical queue
 * rank known ({@link OffScreen}).
 *
 * <p>Computed at placement time by
 * {@link com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout#computeScreenSlot},
 * before an order has ever been rendered, and restamped authoritatively by
 * {@code OrdersScreenDataSource} on every screen reconciliation thereafter.
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

    /** Rendered at container slot {@code slot} on the Orders page. */
    record OnScreen(int slot) implements OrderSlotPosition {
        static final MapCodec<OnScreen> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("slot").forGetter(OnScreen::slot))
                        .apply(instance, OnScreen::new));
    }

    /**
     * Beyond the visible rows. {@code logicalPos} is the order's 0-based rank within its
     * full product/side queue, independent of whether that queue is even partially
     * rendered right now.
     */
    record OffScreen(int logicalPos) implements OrderSlotPosition {
        static final MapCodec<OffScreen> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(Codec.INT.fieldOf("logicalPos").forGetter(OffScreen::logicalPos))
                        .apply(instance, OffScreen::new));
    }

    /** {@code true} when this position is {@link OnScreen} — the order is currently rendered. */
    default boolean isVisible() {
        return this instanceof OnScreen;
    }

    /** {@code true} when this is {@link OnScreen} at exactly {@code slot}. */
    default boolean isOnScreenAt(int slot) {
        return this instanceof OnScreen(int index) && index == slot;
    }

    /** The container slot index, if {@link OnScreen}; empty otherwise. */
    default OptionalInt indexIfVisible() {
        return this instanceof OnScreen(int slot) ? OptionalInt.of(slot) : OptionalInt.empty();
    }

    default String describe() {
        return switch (this) {
            case OnScreen(int slot) -> "slot=" + slot;
            case OffScreen(int logicalPos) -> "off-screen@" + logicalPos;
        };
    }
}