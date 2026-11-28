package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.data.stored.ProfileFlagsStorage;
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
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;
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
            // Avoid synthesizing ghost CoopUnknown orders (or logging errors on solo profiles)
            // when a matching order was already marked filled by FillInference or OrdersScreen.
            boolean alreadyRecordedFilled = storage.stream()
                    .filter(Order::isActionable)
                    .filter(Order.forProduct(productId, transaction.getSide()))
                    .filter(order -> order.originalAmount() == volume)
                    .anyMatch(Order::isFilled);

            if (alreadyRecordedFilled) {
                Util.logMessage("%s — Fill skipped — matching filled order already tracked (%s %s %dx)"
                        .formatted(origin.describe(), productId, transaction.getSide(), volume));
                return;
            }

            if (!ProfileFlagsStorage.isKnownCoop(key)) {
                Util.logMessage("%s — Fill matched no tracked order on solo profile (product=%s type=%s volume=%d)"
                        .formatted(origin.describe(), productId, type, volume));
                return;
            }

            long expiresAt = origin.timestamp() + Order.ORDER_EXPIRY_MS;

            var synthesized = new Order(
                    UUID.randomUUID(), productId, transaction.getSide(),
                    PriceInfo.implied(0.0, volume), volume, volume, 0,
                    new OrderSlotPosition.OffScreen(OrdersPageLayout.SCREEN_CAPACITY),
                    new OrderStatus.Filled(origin.timestamp()),
                    origin.timestamp(), origin.timestamp(),
                    OrderAttribution.CoopUnknown.INSTANCE,
                    Optional.of(expiresAt));

            PlayerActionUtil.notifyAll("%s — Synthesized filled order (coop peer, identity unconfirmed): %s".formatted(origin.describe(), synthesized.describe()), NotificationType.ORDERDATA);

            var placement = new OrderDelta.Place<>(synthesized, BookMutation.none());

            // No book mutation happened; persist directly without running FillInference or BazaarDataUpdateEvent.
            var committed = UserOrdersStorage.apply(
                    key,
                    UserOrdersStorage.StorageOp.add(placement.order())
                            .then(UserOrdersStorage.StorageOp.reindexIfOffsets(null, placement.order())));

            var placed = UserOrdersStorage.findById(committed, placement.order().id())
                    .orElse(placement.order());

            new UserOrderEvent.Placed(placed, key).post(BazaarUtils.EVENT_BUS);

            var fillEvent = placement.initialFillEvent(placed, key);
            if (fillEvent != null) fillEvent.post(BazaarUtils.EVENT_BUS);

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
        var update = OrderDelta.Update.fill(target, filled, mutation);

        var outcome = update.mutation().apply(update.productId(), origin);

        var result = FillInference.applyAndSettle(
                key, List.of(outcome), origin,
                UserOrdersStorage.StorageOp.replace(update.before(), ignored -> update.after())
                        .then(UserOrdersStorage.StorageOp.reindexIfOffsets(update.before(), update.after())));

        var resolved = UserOrdersStorage.findById(result.ordersOrElse(key, List.of()), update.before().id())
                .orElse(update.after());

        update.kind().getEvent(update.before(), resolved, key).post(BazaarUtils.EVENT_BUS);

        if (outcome.changed() || !result.changedProducts().isEmpty()) {
            new BazaarDataUpdateEvent(update.productId(), origin).post(BazaarUtils.EVENT_BUS);
        }
    }
}
