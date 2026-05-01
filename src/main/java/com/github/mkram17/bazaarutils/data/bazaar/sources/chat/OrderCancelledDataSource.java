package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;

@DataSource
public final class OrderCancelledDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var candidates = OrderResolver.forBuyCancelCandidates(event.refundedCoins, storage);

        applyCancel(origin, key, TransactionType.BUY_ORDER, event.refundedCoins, candidates);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Cancel skipped (unknown product) — name=%s".formatted(event.product));

            return;
        }

        var origin = new BazaarDataOrigin.OrderCancelled(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var candidates = OrderResolver.forSellCancelCandidates(product.getProductId(), event.amount, storage);

        applyCancel(origin, key, TransactionType.SELL_OFFER, event.amount, candidates);
    }

    private void applyCancel(@NotNull BazaarDataOrigin.OrderCancelled origin, @NotNull ProfileKey key, @NotNull TransactionType transaction, double refunded, @NotNull List<Order> candidates) {
        if (candidates.isEmpty()) {
            Util.logMessage("Cancel skipped (no candidate — already screen-reconciled, or untracked) — type=%s refunded=%.4f".formatted(
                    transaction.getPriceType(), refunded));

            return;
        }

        var matched = OrderResolver.selectCancelTarget(candidates, refunded);

        if (candidates.size() > 1) {
            Util.logMessage("%s — Cancel ambiguous among %d candidates, choice: %s".formatted(
                    origin.describe(), candidates.size(), matched.describe()));
        }

        var cancelled = matched.cancelled(origin);

        PlayerActionUtil.notifyAll("%s — Cancelled: %s".formatted(origin.describe(), cancelled.describe()), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                origin.describe(),
                transaction.getPriceType(),
                matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);

        var mutation = BookMutation.withdrawn(transaction, matched.pricePerItem(), matched.unfilledAmount());
        var delta = new OrderDelta.Evict<>(matched, cancelled, mutation);

        this.commit(delta, origin, key);
    }
}