package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.TransactionAPI;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.*;
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
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DataSource
public final class OrderFlippedDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onOrderFlipped(BazaarChatEvent.BuyOrderFlipped event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Flip skipped — unknown product: %s".formatted(event.product));
            return;
        }

        var transaction = TransactionType.SELL_OFFER;
        var type = transaction.getPriceType();
        var oppositeType = type.opposite();

        var origin = new BazaarDataOrigin.OrderFlipped(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var productId = product.getProductId();

        int flipVolume = event.amount;
        double profitPerUnit = event.totalProfit / event.amount;

        var matchedBuy = OrderResolver.forFlip(productId, flipVolume, storage).orElse(null);
        if (matchedBuy == null) {
            TransactionAPI.consumeFlip();
            Util.logMessage("%s — Flip skipped (no matching buy order) — %s vol=%d profitPerUnit=%.4f"
                    .formatted(origin.describe(), productId, flipVolume, profitPerUnit));
            return;
        }

        // Hypixel reports Order Flipped! expected profit pre-tax (rounded HALF_UP to whole coins >= 10,000).
        double chatSellPrice = Util.truncateNum(matchedBuy.pricePerItem() + profitPerUnit);

        PriceInfo price = OrderResolver.resolveForFlip(matchedBuy, flipVolume, chatSellPrice);

        double pricePerUnit = price.pricePerItem();
        boolean isPriceExact = price.isExact();

        // A flip can still immediately match resting bids if market bids rose above the original buy price.
        var data = BazaarDataRegistry.get(productId);
        long crossed = data != null ? data.estimateCrossVolume(oppositeType, pricePerUnit, flipVolume) : 0L;

        OrderStatus initialStatus = OrderStatus.FillState.of((int) crossed, flipVolume, origin.confirmedAt());

        boolean isFilled = initialStatus instanceof OrderStatus.Filled;

        // A flip claims the buy order's entire unclaimed fill — nothing is left partially claimed.
        var claimedBuy = matchedBuy.withClaim(matchedBuy.unclaimedFilled(), origin);
        var slotPosition = OrdersPageLayout.computeScreenSlot(productId, transaction, pricePerUnit, origin.confirmedAt(), isFilled, storage);

        long expiresAt = origin.confirmedAt() + Order.ORDER_EXPIRY_MS;

        var attribution = ProfileFlagsStorage.isKnownCoop(key)
                ? OrderAttribution.SelfInCoop.INSTANCE
                : OrderAttribution.Self.INSTANCE;

        var newSell = new Order(
                UUID.randomUUID(), productId, transaction.getSide(),
                price, flipVolume,
                (int) crossed, 0, slotPosition,
                initialStatus, origin.confirmedAt(), origin.confirmedAt(), attribution,
                Optional.of(expiresAt));

        PlayerActionUtil.notifyAll("%s — Flipped %s %dx: buy @ %.4f → sell @ %.4f%s (Δprofit/unit=%.4f)".formatted(
                origin.describe(), productId, flipVolume,
                matchedBuy.pricePerItem(), pricePerUnit, isPriceExact ? "" : "~", profitPerUnit), NotificationType.ORDERDATA);

        int restingVolume = (int) (flipVolume - crossed);
        if (restingVolume > 0) {
            PlayerActionUtil.notifyAll("%s — Book place: %s %s Δ%d @ %.4f (flip)".formatted(
                    origin.describe(), type,
                    productId, restingVolume, pricePerUnit), NotificationType.BAZAARDATA);
        }

        if (crossed > 0) {
            PlayerActionUtil.notifyAll("%s — Book walk: %s %s Δ%d crossed ≤ %.4f (flip)".formatted(
                    origin.describe(), oppositeType,
                    productId, (int) crossed, pricePerUnit), NotificationType.BAZAARDATA);
        }

        var mutation = BookMutation.place(type, pricePerUnit, restingVolume)
                .then(BookMutation.walkUpTo(oppositeType, crossed, pricePerUnit));
        var delta = new OrderDelta.Swap<>(matchedBuy, claimedBuy, newSell, mutation);

        var outcome = delta.mutation().apply(delta.productId(), origin);

        var result = FillInference.applyAndSettle(
                key, List.of(outcome), origin,
                UserOrdersStorage.StorageOp.replace(delta.buyBefore(), ignored -> delta.buyAfter())
                        .then(UserOrdersStorage.StorageOp.add(delta.newSell()))
                        .then(UserOrdersStorage.StorageOp.reindex()));

        var inserted = UserOrdersStorage.findById(result.ordersOrElse(key, List.of()), delta.newSell().id()).orElse(delta.newSell());

        new UserOrderEvent.Flipped(delta.buyAfter(), inserted, key).post(BazaarUtils.EVENT_BUS);

        var fillEvent = delta.initialFillEvent(inserted, key);
        if (fillEvent != null) fillEvent.post(BazaarUtils.EVENT_BUS);

        if (outcome.changed() || !result.changedProducts().isEmpty()) {
            new BazaarDataUpdateEvent(delta.productId(), origin).post(BazaarUtils.EVENT_BUS);
        }
    }
}
