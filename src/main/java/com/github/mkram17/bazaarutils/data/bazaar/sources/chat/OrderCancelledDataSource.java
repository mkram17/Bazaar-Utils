package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.*;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;

@DataSource
public final class OrderCancelledDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderCancelled(BazaarChatEvent.BuyOrderCancelled event) {
        applyCancel(null, TransactionType.BUY_ORDER, event.refundedCoins, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferCancelled(BazaarChatEvent.SellOfferCancelled event) {
        applyCancel(event.product, TransactionType.SELL_OFFER, event.amount, event.receivedAt);
    }

    /**
     * Resolves the cancelled order via {@link OrderResolver#forCancelCandidates} and
     * {@link OrderResolver#selectCancelTarget}, and evicts it.
     *
     * @param productDisplayName the product the message names, or {@code null} for a buy
     *                           cancellation, which reports only the coins refunded
     * @param refunded           what the message reports refunded, in its own unit: coins for a
     *                           buy order, items for a sell offer
     */
    private void applyCancel(@Nullable String productDisplayName, @NotNull TransactionType transaction, double refunded, long receivedAt) {
        String productId = null;

        if (productDisplayName != null) {
            var product = ProductInfo.fromDisplayName(productDisplayName).orElse(null);
            if (product == null) {
                Util.logMessage("Cancel skipped (unknown product) — name=%s".formatted(productDisplayName));
                return;
            }

            productId = product.getProductId();
        }

        var origin = new BazaarDataOrigin.OrderCancelled(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var candidates = OrderResolver.forCancelCandidates(productId, transaction, refunded, storage);
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

        // Only withdraw from the book if the order was still resting on it (not already Expired).
        if (!matched.isOpen()) {
            UserOrdersStorage.apply(
                    key,
                    UserOrdersStorage.StorageOp.replace(matched, ignored -> cancelled)
                            .then(UserOrdersStorage.StorageOp.reindexIfOffsets(matched, cancelled)));

            new UserOrderEvent.Cancelled(cancelled, key).post(BazaarUtils.EVENT_BUS);
            return;
        }

        var mutation = BookMutation.<BazaarDataOrigin.OrderCancelled>withdrawn(transaction, matched.pricePerItem(), matched.unfilledAmount());
        var delta = new OrderDelta.Evict<>(matched, cancelled, mutation);

        var outcome = delta.mutation().apply(delta.productId(), origin);
        if (outcome.changed()) {
            PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (cancelled)".formatted(
                    origin.describe(),
                    transaction.getPriceType(),
                    matched.productId(), matched.unfilledAmount(), matched.pricePerItem()), NotificationType.BAZAARDATA);
        }

        var result = FillInference.applyAndSettle(
                key, List.of(outcome), origin,
                UserOrdersStorage.StorageOp.replace(delta.before(), ignored -> delta.after())
                        .then(UserOrdersStorage.StorageOp.reindexIfOffsets(delta.before(), delta.after())));

        new UserOrderEvent.Cancelled(delta.after(), key).post(BazaarUtils.EVENT_BUS);

        if (outcome.changed() || !result.changedProducts().isEmpty()) {
            new BazaarDataUpdateEvent(delta.productId(), origin).post(BazaarUtils.EVENT_BUS);
        }
    }
}
