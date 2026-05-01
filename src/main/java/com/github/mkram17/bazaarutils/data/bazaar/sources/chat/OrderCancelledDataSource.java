package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
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
 *
 * <p>The two chat messages carry different evidence, so cancellations are matched on
 * different dimensions accordingly. A buy cancellation reports only a coin refund — no
 * product name at all — so {@link OrderResolver#forBuyCancel} matches against
 * {@code pricePerItem × unfilledAmount} across every tracked buy order. A sell
 * cancellation reports both the product and the item count returned, so
 * {@link OrderResolver#forSellCancel} can match directly against the unfilled remainder
 * within that one product.
 *
 * <p>A match failure in either path is not treated as an error — the Orders screen may
 * have already reconciled and evicted the order before this chat message arrived.
 */
@DataSource
public final class OrderCancelledDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    private void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var matched = OrderResolver.forBuyCancel(event.refundedCoins, storage).orElse(null);
        if (matched == null) {
            // Not an error: the Orders screen commonly reconciles and evicts a cancelled
            // order before its own chat confirmation arrives.
            Util.logMessage("Buy cancel skipped (already screen-reconciled) — coinsRefunded=%f".formatted(event.refundedCoins));

            return;
        }

        var cancelled = matched.cancelled(origin);

        PlayerActionUtil.notifyAll("%s — Cancelled — buy order: %s".formatted(origin.describe(), cancelled.describe()), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.BUY_ORDER.getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);

        commit(new OrderDelta.Evict<>(cancelled, BookMutation.decrement(
                TransactionType.BUY_ORDER,
                matched.pricePerItem(), matched.unfilledAmount(), true)), origin, key);
    }

    @Subscription(priority = Priority.FIRST)
    private void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Sell cancel skipped — unknown product: %s".formatted(event.product));

            return;
        }

        var matched = OrderResolver.forSellCancel(product.getProductId(), event.amount, storage).orElse(null);
        if (matched == null) {
            // Same story as the buy path — a prior screen reconciliation likely got there first.
            Util.logMessage("Sell cancel skipped (already screen-reconciled) — productId=%s".formatted(product.getProductId()));

            return;
        }

        var cancelled = matched.cancelled(origin);

        PlayerActionUtil.notifyAll("%s — Cancelled — sell offer: %s".formatted(origin.describe(), cancelled.describe()), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                TransactionType.SELL_OFFER.getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);

        commit(new OrderDelta.Evict<>(cancelled, BookMutation.decrement(
                TransactionType.SELL_OFFER,
                matched.pricePerItem(), matched.unfilledAmount(), true)), origin, key);
    }
}