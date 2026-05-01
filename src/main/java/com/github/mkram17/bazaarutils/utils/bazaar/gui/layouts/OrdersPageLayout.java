package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Screen geometry and slot arithmetic for the Bazaar Orders page.
 *
 * <p>All methods in this class compute <em>display positions only</em> — where orders
 * appear on the Orders page. Display ordering and fill-priority ordering are distinct
 * concepts and must not be conflated. For fill-priority comparisons see
 * {@link Order#byFillPriority(TransactionType.Side)}.
 *
 * <p>Layout:
 * <ul>
 *   <li>Row 0 (slots 0–8): top frame glass, always present.</li>
 *   <li>Content rows (slots 9, 18, 27…): left glass | 7 order slots | right glass.</li>
 *   <li>SELL offers occupy at least one discrete content row; BUY orders follow.</li>
 * </ul>
 * Logical position {@code n} maps to screen slot {@code 10 + (n/7)*9 + (n%7)}.
 */
public final class OrdersPageLayout {
    private static final int CONTENT_START = 10;
    private static final int ROW_STRIDE = 9;
    private static final int COLS = 7;

    public static final int SCREEN_CAPACITY = COLS * 4;

    private OrdersPageLayout() {}

    /**
     * Returns {@code true} when {@code slot} is a valid order cell.
     * Slots in the top and bottom frame rows and border columns are excluded.
     */
    public static boolean isOrderSlot(int slot, int containerSize) {
        if (slot < CONTENT_START) return false;
        if (slot >= containerSize - 9) return false;
        int col = (slot - CONTENT_START) % ROW_STRIDE;

        return col < COLS;
    }

    /** Logical position → screen slot index. */
    public static int toScreenSlot(int logicalPos) {
        return CONTENT_START + (logicalPos / COLS) * ROW_STRIDE + (logicalPos % COLS);
    }

    /**
     * Recomputes {@link OrderSlotPosition} for every live (non-terminal) order and
     * returns the reindexed list. Terminal orders are passed through unchanged.
     */
    public static List<Order> reindexActive(List<Order> orders) {
        var live = orders.stream()
                .filter(order -> !order.isTerminal())
                .toList();

        Util.logMessage("reindexActive — %d orders (%d live)".formatted(orders.size(), live.size()));

        return orders.stream()
                .map(order -> {
                    if (order.isTerminal()) return order;

                    var others = live.stream().filter(it -> !it.id().equals(order.id())).toList();

                    boolean isFilled = order.status() instanceof OrderStatus.Filled;
                    OrderSlotPosition computed = computeScreenSlot(
                            order.productId(), order.side(), order.pricePerItem(),
                            order.placedAt(), isFilled, others);

                    Util.logMessage("reindex %s %s @%f filled=%s %s → %s".formatted(
                            order.productId(), order.side(), order.pricePerItem(),
                            isFilled, order.slotPosition().describe(), computed.describe()));

                    return order.reanchored(computed);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Derives the display {@link OrderSlotPosition} for an order given the full active-order context.
     *
     * <p>Returns the screen slot where the order <em>appears</em> on the Orders page —
     * a display position only. This has no relation to fill priority; for fill-priority
     * ordering see {@link Order#byFillPriority(TransactionType.Side)}.
     *
     * <p>Returns {@link OrderSlotPosition.OnScreen} when logical position is within
     * {@link #SCREEN_CAPACITY}, otherwise {@link OrderSlotPosition.OffScreen}.
     *
     * <p>Ordering rules (Hypixel screen layout):
     * <ol>
     *   <li>SELL offers occupy at least one full row; BUY orders begin on the next row.</li>
     *   <li>Within each side, products are sorted alphabetically by product ID.</li>
     *   <li>Within a product group, filled orders sort first (by {@code placedAt}
     *       ascending), then unfilled orders by price descending, then by
     *       {@code placedAt} ascending.</li>
     * </ol>
     */
    public static OrderSlotPosition computeScreenSlot(
            String productId, TransactionType.Side side, double price,
            long placedAt, boolean isFilled, List<Order> activeOthers) {
        // Terminal orders must never participate in position arithmetic — guard regardless
        // of what the caller passes. Cancelled/Claimed orders retain their last slot position
        // as dead data; including them would corrupt sideOffset, interGroupOffset and intraGroupPos.
        var liveOthers = activeOthers.stream().filter(order -> !order.isTerminal()).toList();

        var sameSideOthers = liveOthers.stream()
                .filter(order -> order.side() == side)
                .toList();

        int sideOffset;
        if (side == TransactionType.Side.BUY) {
            int activeSellCount = (int) liveOthers.stream()
                    .filter(order -> order.side() == TransactionType.Side.SELL)
                    .count();
            int sellRows = Math.max(1, (int) Math.ceil(activeSellCount / (double) COLS));

            sideOffset = sellRows * COLS;
        } else {
            sideOffset = 0;
        }

        int interGroupOffset = (int) sameSideOthers.stream()
                .filter(order -> !order.productId().equals(productId))
                .filter(order -> order.productId().compareTo(productId) < 0)
                .count();

        var sameGroupOthers = sameSideOthers.stream()
                .filter(order -> order.productId().equals(productId))
                .toList();

        // Both BUY and SELL orders are displayed price-descending on the Orders screen.
        // Higher price → lower slot → ranks ahead regardless of side.
        int betterPricedAhead = (int) sameGroupOthers.stream()
                .filter(order -> order.pricePerItem() > price)
                .count();

        int intraGroupPos;
        if (isFilled) {
            // Order::isFilled, not a direct status() check — unwraps Expired, so an
            // Expired(Filled) peer is correctly counted as filled here. This was
            // unreachable before liveOthers/sameGroupOthers started including
            // Expired orders at all; it's live now.
            int samePriceFilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(Order::isFilled)
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();

            intraGroupPos = betterPricedAhead + samePriceFilledOlderAhead;
        } else {
            int samePriceFilledAhead = (int) sameGroupOthers.stream()
                    .filter(Order::isFilled)
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .count();
            int samePriceUnfilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(order -> !order.isFilled())
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();

            intraGroupPos = betterPricedAhead + samePriceFilledAhead + samePriceUnfilledOlderAhead;
        }

        int logicalPos = sideOffset + interGroupOffset + intraGroupPos;

        if (logicalPos >= SCREEN_CAPACITY) {
            return new OrderSlotPosition.OffScreen(logicalPos);
        }

        return new OrderSlotPosition.OnScreen(toScreenSlot(logicalPos));
    }
}