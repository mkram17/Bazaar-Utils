package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.UUID;

/**
 * Handles "Your Buy/Sell Order was filled" chat messages.
 *
 * <p>The target order is selected by competitive position first, then fill priority (FIFO by
 * {@code placedAt}). Any unfilled volume that was never decremented is closed against the book before the fill is recorded.
 * When no stored order matches, a synthetic filled order is inserted so the slot is anchored for screen reconciliation.
 */
@DataSource
public final class OrderFilledDataSource extends ChatOrderSource {
    private static final BazaarLogger LOG = BazaarLogger.of(OrderFilledDataSource.class);

    @Subscription
    public void onBuyOrderFilled(BazaarChatEvent.BuyOrderFilled event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.warn("Fill skipped — unknown product: {}", event.product);

            return;
        }

        applyFill(product.getProductId(), TransactionType.Side.BUY, event.amount, event.receivedAt);
    }

    @Subscription
    public void onSellOfferFilled(BazaarChatEvent.SellOfferFilled event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.warn("Fill skipped — unknown product: {}", event.product);

            return;
        }

        applyFill(product.getProductId(), TransactionType.Side.SELL, event.amount, event.receivedAt);
    }

    private void applyFill(String productId, TransactionType.Side side, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderFilled(receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var candidates = OrderResolver.forFillCandidates(productId, side, volume, storage);

        if (candidates.isEmpty()) {
            // Price is unknown from fill chat — slot cannot be computed meaningfully.
            // OffScreen prevents slot index pollution until orders screen reconciliation corrects it.
            var synthesized = new Order(
                    UUID.randomUUID(), productId, side,
                    0.0, volume, volume, 0,
                    new OrderSlotPosition.OffScreen(0),
                    new OrderStatus.Filled(origin.timestamp()),
                    origin.timestamp(), origin.timestamp(), true);

            PlayerLogger.debug("%s — Synthesized filled order (no prior record, likely coop path): %s".formatted(origin.describe(), synthesized.describe()), NotificationType.ORDER_LIFECYCLE, LOG);

            commit(new OrderDelta.Place(synthesized, BookMutation.NONE), origin);

            return;
        }

        // Registry lookup for competitive-position selection among candidates.
        var data = BazaarDataRegistry.get(productId);
        var target = OrderResolver.selectFillTarget(candidates, data, side);

        // unaccounted = volume that was filled but never decremented from the book.
        // Occurs when screen reconciliation hasn't run between placement and fill,
        // or when the fill chat message arrives before the player opens the orders screen.
        int unaccounted = target.unfilledAmount();

        PlayerLogger.debug("%s — Filled — %s %s %dx @ %.4f (Δunaccounted=%d)".formatted(
                origin.describe(),
                TransactionType.of(side, TransactionType.Method.ORDER).getPriceType(),
                target.productId(), target.originalAmount(), target.pricePerItem(),
                unaccounted), NotificationType.ORDER_LIFECYCLE, LOG);

        if (unaccounted > 0) {
            PlayerLogger.debug("%s — Book decrement: %s %s Δ%d @ %.4f (unaccounted close-out)".formatted(
                    origin.describe(),
                    TransactionType.of(side, TransactionType.Method.ORDER).getPriceType(),
                    target.productId(), unaccounted, target.pricePerItem()), NotificationType.PRICE_DATA, LOG);
        }

        commit(
                OrderDelta.Update.fill(
                        target,
                        target.withFill(unaccounted, origin),
                        BookMutation.decrement(TransactionType.of(side, TransactionType.Method.ORDER), target.pricePerItem(), unaccounted, true)),
                origin);
    }
}