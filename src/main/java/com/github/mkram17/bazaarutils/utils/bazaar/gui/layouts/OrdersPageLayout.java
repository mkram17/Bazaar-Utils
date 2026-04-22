package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Screen geometry and slot arithmetic for the Bazaar Orders page.
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

    private static final BazaarLogger LOG = BazaarLogger.of(OrdersPageLayout.class);

    private static final int CONTENT_START = 10;
    private static final int ROW_STRIDE = 9;
    private static final int COLS = 7;

    private OrdersPageLayout() {}

    /**
     * Returns {@code true} when {@code slot} is a valid order cell.
     * Slots in the top and bottom frame row (0–8) and border columns (every 8th slot
     * in the content area) are excluded.
     */
    public static boolean isOrderSlot(int slot, int containerSize) {
        if (slot < CONTENT_START) return false;
        if (slot >= containerSize - 9) return false; // bottom frame is always the last row
        int col = (slot - CONTENT_START) % ROW_STRIDE;
        return col < COLS;
    }

    /** Logical position → screen slot index. */
    public static int toScreenSlot(int logicalPos) {
        return CONTENT_START + (logicalPos / COLS) * ROW_STRIDE + (logicalPos % COLS);
    }

    public static List<Order> reindexIfFilled(List<Order> orders, Order pivot) {
        return pivot.isFilled() ? reindexActive(orders) : orders;
    }

    /**
     * Recomputes {@code lastKnownIndex} for every active (non-terminal) order
     * and returns the reindexed list. Terminal orders are passed through unchanged.
     */
    public static List<Order> reindexActive(List<Order> orders) {
        var active = orders.stream()
                .filter(Order::isLive)
                .toList();

        LOG.debug("reindexActive — {} orders ({} active)", orders.size(), active.size());

        return orders.stream()
                .map(order -> {
                    if (order.isTerminal()) return order;

                    var others = active.stream().filter(it -> !it.id().equals(order.id())).toList();

                    boolean isFilled = order.status() instanceof OrderStatus.Filled;
                    int computed = computeScreenSlot(order.productId(), order.side(), order.pricePerItem(), order.placedAt(), isFilled, others);

                    LOG.debug("reindex {} {} @{} filled={} slot {} → {}", order.productId(), order.side(), order.pricePerItem(), isFilled, order.lastKnownIndex(), computed);

                    return order.reanchored(computed);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Derives the screen slot for an order given the full active-order context.
     *
     * <p>Ordering rules (Hypixel screen layout):
     * <ol>
     *   <li>SELL offers occupy at least one full row; BUY orders begin on the next row.</li>
     *   <li>Within each side, products are sorted alphabetically by product ID.</li>
     *   <li>Within a product group, filled orders sort first (by {@code placedAt} ascending),
     *       then unfilled orders by price descending, then by {@code placedAt} ascending.</li>
     * </ol>
     */
    public static int computeScreenSlot(
            String productId, TransactionType.Side side, double price,
            long placedAt, boolean isFilled, List<Order> activeOthers) {

        var sameSideOthers = activeOthers.stream()
                .filter(order -> order.side() == side)
                .toList();

        int sideOffset;
        if (side == TransactionType.Side.BUY) {
            int activeSellCount = (int) activeOthers.stream()
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

        // Higher-price orders (any status) are always ahead
        int higherPriceAhead = (int) sameGroupOthers.stream()
                .filter(order -> order.pricePerItem() > price)
                .count();

        int intraGroupPos;
        if (isFilled) {
            int samePriceFilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(order -> order.status() instanceof OrderStatus.Filled)
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();
            intraGroupPos = higherPriceAhead + samePriceFilledOlderAhead;
        } else {
            int samePriceFilledAhead = (int) sameGroupOthers.stream()
                    .filter(order -> order.status() instanceof OrderStatus.Filled)
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .count();
            int samePriceUnfilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(order -> !(order.status() instanceof OrderStatus.Filled))
                    .filter(order -> Double.compare(order.pricePerItem(), price) == 0)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();
            intraGroupPos = higherPriceAhead + samePriceFilledAhead + samePriceUnfilledOlderAhead;
        }

        int logicalPos = sideOffset + interGroupOffset + intraGroupPos;

        // Orders past the screen's 28-slot capacity are not rendered; leave them unanchored
        // rather than mapping them to frame/invalid slots.
        if (logicalPos >= COLS * 4) return Order.UNANCHORED;

        return toScreenSlot(logicalPos);
    }
}