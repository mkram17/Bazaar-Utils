package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handles order cancellation chat messages for both sides.
 *
 * <p><b>Buy cancel:</b> Hypixel's message carries only refunded coins and no item
 * name. The order is resolved by {@link OrderMatcher#buyCancel} which matches
 * {@code pricePerItem × unfilledAmount} against the refunded amount.
 *
 * <p><b>Sell cancel:</b> Chat provides item name and returned volume.
 * {@link OrderMatcher#sellCancel} matches on unfilled remainder.
 */
@DataSource
public final class OrderCancelledDataSource extends BUListener {

    @Subscription
    private void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        applyBuyCancel(event.getRefundedCoins(), event.getReceivedAt());
    }

    @Subscription
    private void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        applySellCancel(event.getOrder(), event.getReceivedAt());
    }

    // ── Buy cancel ────────────────────────────────────────────────────────────

    private static void applyBuyCancel(double coinsRefunded, long receivedAt) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            Util.notifyError("Failed to source order cancelling; Orders storage was not loaded.", new Throwable());
            return;
        }

        var source = new DataSources.OrderCancelled(receivedAt);

        var matched = storage.stream()
                .filter(order -> order.side() == TransactionType.Side.BUY)
                .filter(Order::isActive)
                .filter(order -> OrderMatcher.buyCancel(order, coinsRefunded))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            PlayerActionUtil.notifyAll("Buy cancel skipped — screen already reconciled | coinsRefunded=" + coinsRefunded, NotificationType.BAZAARDATA);

            return;
        }

        var data = BazaarDataRegistry.get(matched.productId());
        if (data == null) return;

        data.decrement(TransactionType.Side.BUY, matched.pricePerItem(), matched.unfilledAmount(), source);

        var cancelled = UserOrdersStorage.cancelAndReindex(matched);

        new BazaarDataUpdateEvent(matched.productId(), source).post(EVENT_BUS);
        new UserOrderEvent.Cancelled(cancelled).post(EVENT_BUS);

        UserOrdersStorage.persist();

        PlayerActionUtil.notifyAll(source.describe()
                + " | " + matched.describe(), NotificationType.BAZAARDATA);
    }

    // ── Sell cancel ───────────────────────────────────────────────────────────

    private static void applySellCancel(OrderInfo info, long receivedAt) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            Util.notifyError("Failed to source order cancelling; Orders storage was not loaded.", new Throwable());
            return;
        }

        var source = new DataSources.OrderCancelled(receivedAt);

        var data = BazaarDataRegistry.get(info.getProductId());
        if (data == null) return;

        var matched = storage.stream()
                .filter(Order.forProduct(info.getProductId(), TransactionType.Side.SELL))
                .filter(Order::isActive)
                .filter(order -> OrderMatcher.sellCancel(order, info.getVolume()))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            PlayerActionUtil.notifyAll("Sell cancel skipped — screen already reconciled | productId=" + info.getProductId(), NotificationType.BAZAARDATA);

            return;
        }

        data.decrement(TransactionType.Side.SELL, matched.pricePerItem(), matched.unfilledAmount(), source);

        var cancelled = UserOrdersStorage.cancelAndReindex(matched);

        new UserOrderEvent.Cancelled(cancelled).post(EVENT_BUS);
        new BazaarDataUpdateEvent(matched.productId(), source).post(EVENT_BUS);

        UserOrdersStorage.persist();

        PlayerActionUtil.notifyAll(source.describe()
                + " | " + matched.describe(), NotificationType.BAZAARDATA);
    }
}