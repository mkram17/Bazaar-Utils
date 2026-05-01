package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.data.stored.BazaarProfileFlags;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;
import java.util.UUID;

@DataSource
public final class OrderFilledDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderFilled(BazaarChatEvent.BuyOrderFilled event) {
        applyFill(event.product, TransactionType.BUY_ORDER, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferFilled(BazaarChatEvent.SellOfferFilled event) {
        applyFill(event.product, TransactionType.SELL_OFFER, event.amount, event.receivedAt);
    }

    private void applyFill(@NotNull String productDisplayName, @NotNull TransactionType transaction, int volume, long receivedAt) {
        var product = ProductInfo.fromDisplayName(productDisplayName).orElse(null);
        if (product == null) {
            Util.logMessage("Fill skipped — unknown product: %s".formatted(productDisplayName));

            return;
        }

        var origin = new BazaarDataOrigin.OrderFilled(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var type = transaction.getPriceType();

        var productId = product.getProductId();

        var candidates = OrderResolver.forFillCandidates(productId, transaction, volume, storage);

        if (candidates.isEmpty()) {
            if (!BazaarProfileFlags.isKnownCoop(key)) {
                Util.notifyError("%s — Fill matched no tracked order on a non-coop profile (product=%s type=%s volume=%d)".formatted(
                        origin.describe(), productId, type, volume), new Throwable());

                return;
            }

            long expiresAt = origin.timestamp() + PageOrderParser.ORDER_EXPIRY_MS;

            var synthesized = new Order(
                    UUID.randomUUID(), productId, transaction.getSide(),
                    false, 0.0, volume, volume, 0,
                    new OrderSlotPosition.OffScreen(0),
                    new OrderStatus.Filled(origin.timestamp()),
                    origin.timestamp(), origin.timestamp(),
                    new OrderAttribution.CoopUnknown(),
                    Optional.of(expiresAt));

            PlayerActionUtil.notifyAll("%s — Synthesized filled order (coop peer, identity unconfirmed): %s".formatted(origin.describe(), synthesized.describe()), NotificationType.ORDERDATA);

            commit(new OrderDelta.Place<>(synthesized, BookMutation.none()), origin, key);

            return;
        }

        var target = OrderResolver.selectFillTarget(candidates, transaction);

        // Unfilled volume the book was never decremented for — reconciliation hadn't
        // caught up, or this message beat the player's next Orders screen open.
        int unaccounted = target.unfilledAmount();

        var filled = target.withFill(unaccounted, origin);

        PlayerActionUtil.notifyAll("%s — Filled (Δunaccounted=%d): %s".formatted(
                origin.describe(), unaccounted, filled.describe()), NotificationType.ORDERDATA);

        if (unaccounted > 0) {
            PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (unaccounted close-out)".formatted(
                    origin.describe(),
                    type,
                    target.productId(), unaccounted, target.pricePerItem()), NotificationType.BAZAARDATA);
        }

        var mutation = BookMutation.filled(target.id(), transaction, target.pricePerItem(), unaccounted, filled.isFilled())
                .then(BookMutation.evictAhead(transaction, target.pricePerItem()));
        var delta = OrderDelta.Update.fill(target, filled, mutation);

        commit(delta, origin, key);
    }
}