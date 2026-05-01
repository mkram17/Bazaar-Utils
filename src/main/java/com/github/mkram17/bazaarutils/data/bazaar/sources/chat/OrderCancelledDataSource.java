package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

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
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Evicts a cancelled order and decrements its unfilled volume from the book.
 */
@DataSource
public final class OrderCancelledDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    private void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var storage = requireStorage(origin);
        if (storage == null) return;

        var matched = OrderResolver.forBuyCancel(event.refundedCoins, storage).orElse(null);
        if (matched == null) {
            // Not an error: the orders screen may have reconciled and evicted this order
            // before the chat cancellation message arrived.
            Util.logMessage("Buy cancel skipped (already screen-reconciled) — coinsRefunded=%f".formatted(event.refundedCoins));

            return;
        }

        var cancelled = matched.cancelled(origin);

        PlayerActionUtil.notifyAll("%s — Cancelled — buy order: %s".formatted(origin.describe(), cancelled.describe()), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER).getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);

        commit(new OrderDelta.Evict(cancelled, BookMutation.decrement(
                TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER),
                matched.pricePerItem(), matched.unfilledAmount(), true)), origin);
    }

    @Subscription(priority = Priority.FIRST)
    private void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Sell cancel skipped — unknown product: %s".formatted(event.product));

            return;
        }

        var matched = OrderResolver.forSellCancel(product.getProductId(), event.amount, storage).orElse(null);
        if (matched == null) {
            Util.logMessage("Sell cancel skipped (already screen-reconciled) — productId=%s".formatted(product.getProductId()));

            return;
        }

        var cancelled = matched.cancelled(origin);

        PlayerActionUtil.notifyAll("%s — Cancelled — sell offer: %s".formatted(origin.describe(), cancelled.describe()), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER).getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);

        commit(new OrderDelta.Evict(cancelled, BookMutation.decrement(
                TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER),
                matched.pricePerItem(), matched.unfilledAmount(), true)), origin);
    }
}