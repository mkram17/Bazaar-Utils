package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.data.stored.BazaarProfileFlags;
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
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.UUID;

/**
 * Advances an order to {@link OrderStatus.Filled} when a fill completion chat message
 * arrives, or synthesizes one when no tracked order exists to advance.
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
     * Locates the filled order among candidates of matching original volume and commits
     * its transition to {@link OrderStatus.Filled}.
     *
     * <p>An empty candidate list on a profile not known to be coop is treated as a
     * resolver bug and reported, not silently papered over — there's no legitimate reason
     * for a solo profile to receive a fill message for an order it has no record of. On a
     * known-coop profile, it's instead synthesized as a peer's order:
     * {@link OrderSlotPosition.OffScreen} at position 0, since the fill message carries no
     * price to compute a real slot from, {@link OrderAttribution.CoopUnknown} since the
     * peer's identity is unconfirmed, and a best-effort {@code receivedAt + 7 days} expiry
     * since the true {@code placedAt} is unknown. No book mutation accompanies the
     * synthesis — there's no price to decrement against.
     *
     * <p>When a candidate is found, {@code unaccounted} is whatever unfilled volume the
     * order still carries — fill that happened without a prior book decrement, because
     * screen reconciliation hadn't run between placement and this message. The book
     * mutation closes that out at the target's own price, then evicts every level strictly
     * better-priced than it — a chat-confirmed full fill is proof the market already
     * cleared everything ahead of this order in the queue too, whether or not those levels
     * were individually decremented as they cleared. See
     * {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData.WalkOp#ahead}.
     */
    private void applyFill(String productId, TransactionType.Side side, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderFilled(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var candidates = OrderResolver.forFillCandidates(productId, side, volume, storage);

        if (candidates.isEmpty()) {
            // key came from requireProfile(), so it is the active profile — isKnownCoop's
            // live ProfileAPI check will actually run here, not fall back to a possibly
            // stale persisted value the way it would for any other profile.
            if (!BazaarProfileFlags.isKnownCoop(key)) {
                Util.notifyError("%s — fill matched no tracked order on a non-coop profile (product=%s side=%s volume=%d) — resolver bug, not synthesizing".formatted(
                        origin.describe(), productId, side, volume), new Throwable());

                return;
            }

            var synthesized = new Order(
                    UUID.randomUUID(), productId, side,
                    0.0, volume, volume, 0,
                    new OrderSlotPosition.OffScreen(0),
                    new OrderStatus.Filled(origin.timestamp()),
                    origin.timestamp(), origin.timestamp(),
                    new OrderAttribution.CoopUnknown(),
                    origin.timestamp() + 7L * 24 * 3_600_000L);

            PlayerActionUtil.notifyAll("%s — Synthesized filled order (coop peer, identity unconfirmed): %s".formatted(origin.describe(), synthesized.describe()), NotificationType.ORDERDATA);

            commit(new OrderDelta.Place<>(synthesized, BookMutation.none()), origin, key);

            return;
        }

        // Registry lookup only for competitive-position tiebreaking among same-size candidates.
        var data = BazaarDataRegistry.get(productId);
        var target = OrderResolver.selectFillTarget(candidates, data, side);
        var transaction = TransactionType.of(side, TransactionType.Method.ORDER);

        // Unfilled volume the book was never decremented for — reconciliation hadn't
        // caught up, or this message beat the player's next Orders screen open.
        int unaccounted = target.unfilledAmount();

        PlayerActionUtil.notifyAll("%s — Filled — %s %s %dx @ %.4f (Δunaccounted=%d)".formatted(
                origin.describe(),
                transaction.getPriceType(),
                target.productId(), target.originalAmount(), target.pricePerItem(),
                unaccounted), NotificationType.ORDERDATA);

        if (unaccounted > 0) {
            PlayerActionUtil.notifyAll("%s — Book decrement: %s %s Δ%d @ %.4f (unaccounted close-out)".formatted(
                    origin.describe(),
                    transaction.getPriceType(),
                    target.productId(), unaccounted, target.pricePerItem()), NotificationType.BAZAARDATA);
        }

        var mutation = BookMutation.decrement(transaction, target.pricePerItem(), unaccounted, true)
                .then(BookMutation.evictAhead(transaction, target.pricePerItem()));

        commit(OrderDelta.Update.fill(target, target.withFill(unaccounted, origin), mutation), origin, key);
    }
}