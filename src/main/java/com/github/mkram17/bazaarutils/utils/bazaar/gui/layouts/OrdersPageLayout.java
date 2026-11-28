package com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderAttribution;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderSlotPosition;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Screen geometry and slot arithmetic for the Bazaar Orders page.
 *
 * <p>All methods in this class compute <em>display positions only</em> — where orders
 * appear on the Orders page. Display ordering and fill-priority ordering are distinct
 * concepts and must not be conflated. For fill-priority comparisons see
 * {@link Order#byFillPriority(PriceType)}.
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
    private static final int ROWS = 4;

    private static final int RESERVED_ROWS_FOR_OTHER_SIDE = 1;

    public static final int SCREEN_CAPACITY = COLS * ROWS;

    /**
     * Strict total display ordering within one side of the Orders page:
     * productId ascending → price descending → filled before unfilled → placedAt ascending → id ascending.
     */
    private static final Comparator<Order> BY_DISPLAY_ORDER = Comparator
            .comparing(Order::productId)
            .thenComparing(Comparator.comparingDouble(Order::pricePerItem).reversed())
            .thenComparing((Order order) -> !order.isFilled())
            .thenComparingLong(Order::placedAt)
            .thenComparing(Order::id);

    private OrdersPageLayout() {}

    /**
     * {@code true} when an order participates in Orders-page slot layout.
     * Excludes terminal orders and price-less synthetic {@link OrderAttribution.CoopUnknown}
     * placeholders whose real price & slot have not yet been observed on screen.
     */
    private static boolean isLayoutAnchored(Order order) {
        return order.isActionable()
                && !(order.attribution() instanceof OrderAttribution.CoopUnknown && order.pricePerItem() <= 0.0);
    }

    /**
     * Content rows SELL currently occupies, and therefore how far BUY's own region is
     * pushed down. Clamped to leave at least one row for each side regardless of the
     * other's count, including zero. Factored out of {@link #computeScreenSlot}'s inline
     * computation so {@link #isSideExhaustive} can't drift from the same rule.
     */
    private static int sellRowsFor(int activeSellCount) {
        return Math.clamp((int) Math.ceil(activeSellCount / (double) COLS), RESERVED_ROWS_FOR_OTHER_SIDE, ROWS - RESERVED_ROWS_FOR_OTHER_SIDE);
    }

    /**
     * {@code true} when {@code side}'s own count proves this render shows every order of
     * that side the profile currently has. {@code false} only means this can't rule an
     * overflow out — it isn't itself proof one happened.
     *
     * <p>Not a flat comparison against {@link #SCREEN_CAPACITY}. Each side is guaranteed at
     * least one row even when the other side is entirely empty: SELL's own ceiling sits at
     * {@code SCREEN_CAPACITY - COLS} regardless of how few BUY orders exist, and BUY's is
     * pushed down by however many rows SELL is currently using — never fewer than one, even
     * at zero.
     *
     * @param activeCount    how many of {@code side}'s orders this render actually shows
     * @param otherSideCount how many of the OTHER side's orders this render shows — for
     *                       BUY, this is what decides how many rows are actually left
     */
    public static boolean isSideExhaustive(TransactionType.Side side, int activeCount, int otherSideCount) {
        int capacity = side == TransactionType.Side.BUY
                ? SCREEN_CAPACITY - sellRowsFor(otherSideCount) * COLS
                : SCREEN_CAPACITY - RESERVED_ROWS_FOR_OTHER_SIDE * COLS;

        return activeCount < capacity;
    }

    /**
     * Returns {@code true} when {@code slot} is a valid order cell.
     * Slots in the top and bottom frame rows and border columns are excluded.
     */
    public static boolean isOrderSlot(int slot, int containerSize) {
        if (slot < CONTENT_START) return false;
        if (slot >= containerSize - ROW_STRIDE) return false;
        int col = (slot - CONTENT_START) % ROW_STRIDE;

        return col < COLS;
    }

    /** Logical position → screen slot index. */
    public static int toScreenSlot(int logicalPos) {
        return CONTENT_START + (logicalPos / COLS) * ROW_STRIDE + (logicalPos % COLS);
    }

    /**
     * Recomputes {@link OrderSlotPosition} for every live (non-terminal) order using a
     * single total-order sort per side so assigned screen slots are provably unique.
     * Terminal orders are passed through unchanged; price-less {@link OrderAttribution.CoopUnknown}
     * placeholders stay {@link OrderSlotPosition.OffScreen} until confirmed on screen.
     */
    public static List<Order> reindexActive(List<Order> orders) {
        List<Order> sells = new ArrayList<>();
        List<Order> buys = new ArrayList<>();

        for (Order order : orders) {
            if (!isLayoutAnchored(order)) continue;
            if (order.isSellOffer()) sells.add(order);
            else buys.add(order);
        }

        sells.sort(BY_DISPLAY_ORDER);
        buys.sort(BY_DISPLAY_ORDER);

        int sellCapacity = SCREEN_CAPACITY - RESERVED_ROWS_FOR_OTHER_SIDE * COLS;
        int buyOffset = sellRowsFor(sells.size()) * COLS;

        Map<UUID, OrderSlotPosition> computedById = HashMap.newHashMap(sells.size() + buys.size());
        for (int i = 0; i < sells.size(); i++) {
            OrderSlotPosition pos = i < sellCapacity
                    ? new OrderSlotPosition.OnScreen(toScreenSlot(i))
                    : new OrderSlotPosition.OffScreen(i);
            computedById.put(sells.get(i).id(), pos);
        }
        for (int i = 0; i < buys.size(); i++) {
            int logical = buyOffset + i;
            OrderSlotPosition pos = logical < SCREEN_CAPACITY
                    ? new OrderSlotPosition.OnScreen(toScreenSlot(logical))
                    : new OrderSlotPosition.OffScreen(logical);
            computedById.put(buys.get(i).id(), pos);
        }

        List<Order> result = new ArrayList<>(orders.size());
        int movedCount = 0;
        for (Order order : orders) {
            if (order.isTerminal()) {
                result.add(order);
                continue;
            }

            OrderSlotPosition computed = computedById.getOrDefault(
                    order.id(),
                    order.slotPosition() instanceof OrderSlotPosition.OffScreen off
                            ? off
                            : new OrderSlotPosition.OffScreen(SCREEN_CAPACITY)
            );

            if (!computed.equals(order.slotPosition())) {
                movedCount++;
                Util.logMessage("reindex %s %s @%.4f filled=%b %s → %s".formatted(
                        order.productId(), order.side(), order.pricePerItem(),
                        order.isFilled(), order.slotPosition().describe(), computed.describe()));
            }

            result.add(order.reanchored(computed));
        }

        Util.logMessage("reindexActive — %d orders (%d anchored, %d moved)"
                .formatted(orders.size(), sells.size() + buys.size(), movedCount));

        return result;
    }

    /**
     * Derives the display {@link OrderSlotPosition} for an order given the full active-order context.
     *
     * <p>Returns the screen slot where the order <em>appears</em> on the Orders page —
     * a display position only. This has no relation to fill priority; for fill-priority
     * ordering see {@link Order#byFillPriority(PriceType)}.
     *
     * <p>Returns {@link OrderSlotPosition.OnScreen} when logical position is within
     * the side's visible capacity, otherwise {@link OrderSlotPosition.OffScreen}.
     *
     * <p>Ordering rules (Hypixel screen layout):
     * <ol>
     *   <li>SELL offers occupy at least one full row; BUY orders begin on the next row.</li>
     *   <li>Within each side, products are sorted alphabetically by product ID.</li>
     *   <li>Within a product group, orders sort by price descending first; within the same
     *       price, filled orders sort before unfilled orders; ties break by {@code placedAt}
     *       ascending.</li>
     * </ol>
     */
    public static OrderSlotPosition computeScreenSlot(
            @NotNull String productId, @NotNull TransactionType transaction, double pricePerItem,
            long placedAt, boolean isFilled, @NotNull List<Order> activeOthers) {
        var side = transaction.getSide();

        var liveOthers = activeOthers.stream().filter(OrdersPageLayout::isLayoutAnchored).toList();

        var sameSideOthers = liveOthers.stream()
                .filter(order -> order.side() == side)
                .toList();

        int sideOffset;
        int sideCapacity;
        if (side == TransactionType.Side.BUY) {
            int activeSellCount = (int) liveOthers.stream()
                    .filter(order -> order.side() == TransactionType.Side.SELL)
                    .count();

            sideOffset = sellRowsFor(activeSellCount) * COLS;
            sideCapacity = SCREEN_CAPACITY;
        } else {
            sideOffset = 0;
            // Mirrors the BUY branch: SELL can never claim BUY's reserved row, so SELL's own
            // ceiling sits one row short of the full screen — regardless of BUY's actual count,
            // including zero.
            sideCapacity = SCREEN_CAPACITY - RESERVED_ROWS_FOR_OTHER_SIDE * COLS;
        }

        int interGroupOffset = (int) sameSideOthers.stream()
                .filter(order -> !order.productId().equals(productId))
                .filter(order -> order.productId().compareTo(productId) < 0)
                .count();

        var sameGroupOthers = sameSideOthers.stream()
                .filter(Order.forProduct(productId))
                .toList();

        int betterPricedAhead = (int) sameGroupOthers.stream()
                .filter(order -> Double.compare(order.pricePerItem(), pricePerItem) > 0)
                .count();

        int intraGroupPos;
        if (isFilled) {
            int samePriceFilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(Order::isFilled)
                    .filter(Order.forExactPrice(pricePerItem))
                    .filter(order -> order.placedAt() < placedAt)
                    .count();

            intraGroupPos = betterPricedAhead + samePriceFilledOlderAhead;
        } else {
            int samePriceFilledAhead = (int) sameGroupOthers.stream()
                    .filter(Order::isFilled)
                    .filter(Order.forExactPrice(pricePerItem))
                    .count();
            int samePriceUnfilledOlderAhead = (int) sameGroupOthers.stream()
                    .filter(order -> !order.isFilled())
                    .filter(Order.forExactPrice(pricePerItem))
                    .filter(order -> order.placedAt() < placedAt)
                    .count();

            intraGroupPos = betterPricedAhead + samePriceFilledAhead + samePriceUnfilledOlderAhead;
        }

        int logicalPos = sideOffset + interGroupOffset + intraGroupPos;

        if (logicalPos >= sideCapacity) {
            return new OrderSlotPosition.OffScreen(logicalPos);
        }

        return new OrderSlotPosition.OnScreen(toScreenSlot(logicalPos));
    }
}
