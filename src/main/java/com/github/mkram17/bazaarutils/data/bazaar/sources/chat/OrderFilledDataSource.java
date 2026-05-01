package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.UUID;

/**
 * Advances an order to {@link OrderStatus.Filled} when the fill completion chat message arrives.
 */
@DataSource
public final class OrderFilledDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderFilled(BazaarChatEvent.BuyOrderFilled event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Fill skipped — unknown product: %s".formatted(event.product));

            return;
        }

        applyFill(product.getProductId(), TransactionType.Side.BUY, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferFilled(BazaarChatEvent.SellOfferFilled event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Fill skipped — unknown product: %s".formatted(event.product));

            return;
        }

        applyFill(product.getProductId(), TransactionType.Side.SELL, event.amount, event.receivedAt);
    }

    /**
     * Identifies the target order among candidates of matching original volume, closes out any
     * unfilled volume that was filled without a prior book decrement (the {@code unaccounted}
     * amount represents fill that occurred between the last screen reconciliation and this chat
     * event), and commits the update.
     *
     * <p>When no candidates exist — typically a co-op member's order appearing on the shared
     * Orders page — a synthetic {@link Order} is inserted as {@link OrderSlotPosition.OffScreen}
     * with a best-effort 7-day expiry, flagged as a co-op order so screen reconciliation can
     * anchor it to its real slot on the next Orders page open.
     */
    private void applyFill(String productId, TransactionType.Side side, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderFilled(receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var candidates = OrderResolver.forFillCandidates(productId, side, volume, storage);

        if (candidates.isEmpty()) {
            // Price is unknown from fill chat — slot cannot be computed meaningfully.
            // OffScreen prevents slot index pollution until orders screen reconciliation corrects it.
            // This is typically a coop fill. expiresAt is a best-effort stamp since we don't know
            // the real placedAt; receivedAt + 7d is a safe upper bound.
            var synthesized = new Order(
                    UUID.randomUUID(), productId, side,
                    0.0, volume, volume, 0,
                    new OrderSlotPosition.OffScreen(0),
                    new OrderStatus.Filled(origin.timestamp()),
                    origin.timestamp(), origin.timestamp(), true,
                    origin.timestamp() + 7L * 24 * 3_600_000L);

            PlayerActionUtil.notifyAll("%s — Synthesized filled order (no prior record, likely coop path): %s".formatted(origin.describe(), synthesized.describe()), NotificationType.ORDERDATA);

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

        PlayerActionUtil.notifyAll("%s — Filled — %s %s %dx @ %.4f (Δunaccounted=%d)".formatted(
                origin.describe(),
                TransactionType.of(side, TransactionType.Method.ORDER).getPriceType(),
                target.productId(), target.originalAmount(), target.pricePerItem(),
                unaccounted), NotificationType.ORDERDATA);

        if (unaccounted > 0) {
            PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (unaccounted close-out)".formatted(
                    origin.describe(),
                    TransactionType.of(side, TransactionType.Method.ORDER).getPriceType(),
                    target.productId(), unaccounted, target.pricePerItem()), NotificationType.BAZAARDATA);
        }

        commit(
                OrderDelta.Update.fill(
                        target,
                        target.withFill(unaccounted, origin),
                        BookMutation.decrement(TransactionType.of(side, TransactionType.Method.ORDER), target.pricePerItem(), unaccounted, true)),
                origin);
    }
}