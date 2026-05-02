package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Handles order cancellation chat messages.
 *
 * <p>Buy cancel: only refunded coins are present; the stored order is matched by
 * {@code pricePerItem × unfilledAmount ≈ refundedCoins}. Evicts the order and
 * decrements the buy book.
 *
 * <p>Sell cancel: display name and returned item count are present; the stored
 * order is matched on unfilled remainder. Evicts the order and decrements the
 * sell book.
 */
@DataSource
public final class OrderCancelledDataSource extends ChatOrderSource {
    private static final BazaarLogger LOG = BazaarLogger.of(OrderCancelledDataSource.class);

    @Subscription(priority = Priority.FIRST)
    private void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var storage = requireStorage(origin);
        if (storage == null) return;

        var matched = OrderResolver.forBuyCancel(event.refundedCoins, storage).orElse(null);
        if (matched == null) {
            // Not an error: the orders screen may have reconciled and evicted this order
            // before the chat cancellation message arrived.
            LOG.info("Buy cancel skipped (already screen-reconciled) — coinsRefunded={}", event.refundedCoins);

            return;
        }

        PlayerLogger.debug("%s — Cancelled — buy order: %s".formatted(origin.describe(), matched.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

        PlayerLogger.debug("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER).getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.PRICE_DATA, LOG);

        commit(new OrderDelta.Evict(matched, BookMutation.decrement(TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER), matched.pricePerItem(), matched.unfilledAmount(), true)), origin);
    }

    @Subscription(priority = Priority.FIRST)
    private void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.info("Sell cancel skipped — unknown product: {}", event.product);

            return;
        }

        var matched = OrderResolver.forSellCancel(product.getProductId(), event.amount, storage).orElse(null);
        if (matched == null) {
            LOG.info("Sell cancel skipped (already screen-reconciled) — productId={}", product.getProductId());

            return;
        }

        PlayerLogger.debug("%s — Cancelled — sell offer: %s".formatted(origin.describe(), matched.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

        PlayerLogger.debug("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER).getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.PRICE_DATA, LOG);

        commit(new OrderDelta.Evict(matched, BookMutation.decrement(
                TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER),
                matched.pricePerItem(), matched.unfilledAmount(), true)), origin);
    }
}