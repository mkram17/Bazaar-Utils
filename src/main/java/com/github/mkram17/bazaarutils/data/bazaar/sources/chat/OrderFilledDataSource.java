package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

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
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;
import java.util.UUID;

/**
 * Advances an order to {@link OrderStatus.Filled} when a fill completion chat message
 * arrives, or synthesizes one when no tracked order exists to advance.
 */
@DataSource
public final class OrderFilledDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderFilled(BazaarChatEvent.BuyOrderFilled event) {
        applyFill(event.product, TransactionType.BUY_ORDER, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferFilled(BazaarChatEvent.SellOfferFilled event) {
        applyFill(event.product, TransactionType.SELL_OFFER, event.amount, event.receivedAt);
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

        var mutation = BookMutation.filled(transaction, target.pricePerItem(), unaccounted, filled.isFilled())
                .then(BookMutation.evictAhead(transaction, target.pricePerItem()));
        var delta = OrderDelta.Update.fill(target, filled, mutation);

        commit(delta, origin, key);
    }
}