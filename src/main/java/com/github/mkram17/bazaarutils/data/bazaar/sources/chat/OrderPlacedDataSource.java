package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.UUID;

/**
 * Inserts a new tracked order and applies the corresponding optimistic book increment
 * when a placement chat message is received.
 */
@DataSource
public final class OrderPlacedDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderCreated(BazaarChatEvent.BuyOrderCreated event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Placement skipped — unknown product: %s".formatted(event.product));

            return;
        }

        double price = OrderResolver.resolveForPlacement(Util.truncateNum(event.totalCoins / event.amount));

        applyPlacement(product.getProductId(), TransactionType.Side.BUY, price, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferCreated(BazaarChatEvent.SellOfferCreated event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Placement skipped — unknown product: %s".formatted(event.product));

            return;
        }

        double tax = TaxContext.effectiveTaxPercent();
        double chatPrice = Util.truncateNum(Util.truncateNum(event.totalCoins / event.amount) / ((100.0 - tax) / 100.0));

        double price = OrderResolver.resolveForPlacement(chatPrice);

        applyPlacement(product.getProductId(), TransactionType.Side.SELL, price, event.amount, event.receivedAt);
    }

    /**
     * Constructs and commits a new tracked order.
     *
     * <p>The initial fill state is derived from
     * {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData#estimateCrossVolume}
     * against the opposite book side before the order is written: volume that immediately crosses
     * existing offers sets status to {@link OrderStatus.Partial} or {@link OrderStatus.Filled}
     * before the order has ever appeared on the Orders screen.
     *
     * <p>The book mutation places the uncrossed remainder on the own side and walks the crossed
     * volume off the opposite side in one atomic step, keeping both books consistent. An invalid
     * recovered sell price (indicating a {@code BazaarFlipper} tier mismatch) aborts early rather
     * than writing garbage to the book.
     */
    private void applyPlacement(String productId, TransactionType.Side side, double price, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderPlaced(receivedAt);
        var transaction = TransactionType.of(side, TransactionType.Method.ORDER);
        var type = transaction.getPriceType();
        var oppositeType = type.opposite();

        var storage = requireStorage(origin); if (storage == null) return;

        if (side == TransactionType.Side.SELL && !Util.isValidHypixelPrice(price)) {
            TaxContext.warnTaxMisconfiguration("Sell offer price recovery produced an invalid value (%.5f).".formatted(price));

            return;
        }

        var data = BazaarDataRegistry.get(productId);
        long crossed = data != null ? data.estimateCrossVolume(oppositeType, price, volume) : 0L;

        OrderStatus initialStatus;
        if (crossed >= volume) {
            initialStatus = new OrderStatus.Filled(origin.confirmedAt());
        } else if (crossed > 0) {
            initialStatus = new OrderStatus.Partial(origin.confirmedAt(), origin.confirmedAt());
        } else {
            initialStatus = new OrderStatus.Set();
        }

        boolean isFilled = initialStatus instanceof OrderStatus.Filled;

        var slotPosition = OrdersPageLayout.computeScreenSlot(productId, side, price, origin.confirmedAt(), isFilled, storage);

        long expiresAt = origin.confirmedAt() + 7L * 24 * 3_600_000L;

        var order = new Order(UUID.randomUUID(), productId, side,
                price, volume, (int) crossed, 0, slotPosition,
                initialStatus, origin.confirmedAt(), origin.confirmedAt(), false,
                expiresAt);

        PlayerActionUtil.notifyAll("%s — Placed %s %s %dx @ %.4f".formatted(
                origin.describe(), type,
                productId, volume, price), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book place: %s %s Δ%d @ %.4f".formatted(
                origin.describe(), type,
                productId, (int) (volume - crossed), price), NotificationType.BAZAARDATA);

        if (crossed > 0) {
            PlayerActionUtil.notifyAll("%s — Book walk: %s %s Δ%d crossed ≤ %.4f".formatted(
                    origin.describe(), oppositeType,
                    productId, (int) crossed, price), NotificationType.BAZAARDATA);
        }

        commit(new OrderDelta.Place(order,
                        BookMutation.place(type, price, (int) (volume - crossed))
                                .then(BookMutation.walkUpTo(oppositeType, crossed, price))),
                origin);
    }
}