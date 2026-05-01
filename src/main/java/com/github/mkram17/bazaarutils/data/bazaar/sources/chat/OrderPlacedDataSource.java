package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
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
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderAttribution;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DataSource
public final class OrderPlacedDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderCreated(BazaarChatEvent.BuyOrderCreated event) {
        var origin = new BazaarDataOrigin.OrderPlaced(event.receivedAt);
        var key = ProfileKey.requireProfile(origin.describe());
        if (key == null) return;

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Placement skipped — unknown product: %s".formatted(event.product));
            return;
        }

        PriceInfo price = OrderResolver.resolveForPlacement(
                product.getProductId(),
                TransactionType.BUY_ORDER,
                event.amount,
                Util.truncateNum(event.totalCoins / event.amount));

        applyPlacement(product.getProductId(), TransactionType.BUY_ORDER, key, origin, price, event.amount);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferCreated(BazaarChatEvent.SellOfferCreated event) {
        var origin = new BazaarDataOrigin.OrderPlaced(event.receivedAt);
        var key = ProfileKey.requireProfile(origin.describe());
        if (key == null) return;

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Placement skipped — unknown product: %s".formatted(event.product));
            return;
        }

        PriceInfo price = OrderResolver.consumeMatchingPlacement(product.getProductId(), TransactionType.SELL_OFFER, event.amount)
                .map(exact -> (PriceInfo) exact)
                .orElse(null);

        if (price == null) {
            // Hypixel reports Sell Offer Setup totals post-tax, rounded HALF_UP to 0.1 below
            // FOLDING_THRESHOLD (10,000 coins) and to whole coins at or above it.
            double tax = TaxContext.effectiveTaxPercent(key);
            double netFactor = 1.0 - tax / 100.0;
            double denom = event.amount * netFactor;

            if (denom <= 0.0) {
                TaxContext.warnTaxMisconfiguration("Sell offer price recovery failed due to invalid tax factor.");
                return;
            }

            double raw = event.totalCoins / denom;
            double snapped = Util.truncateNum(raw);
            double maxError = (event.totalCoins >= PriceInfo.FOLDING_THRESHOLD ? PriceInfo.COIN_EPSILON : 0.05) / denom + 1e-4;

            // Only flag tax misconfiguration when display rounding noise is below half a 0.1 tick
            // and the recovered value still misses the nearest valid 0.1 tick by more than maxError.
            if (!Util.isValidHypixelPrice(snapped) || (maxError < 0.05 && Math.abs(raw - snapped) > maxError)) {
                TaxContext.warnTaxMisconfiguration(
                        "Sell offer price recovery failed (raw=%.5f, snapped=%.1f, tax=%.2f%%)."
                                .formatted(raw, snapped, tax));
                return;
            }

            price = PriceInfo.implied(snapped, event.amount);
        }

        applyPlacement(product.getProductId(), TransactionType.SELL_OFFER, key, origin, price, event.amount);
    }

    /**
     * Builds and commits a new tracked order for {@code productId}.
     *
     * <p>The order's initial status is decided before it has ever appeared on the Orders
     * screen: {@link com.github.mkram17.bazaarutils.data.bazaar.book.ProductData#estimateCrossVolume}
     * against the opposite book side tells how much of {@code volume} would be matched
     * immediately, and that crossed amount sets the starting status to
     * {@link OrderStatus.Filled}, {@link OrderStatus.Partial}, or {@link OrderStatus.Set}.
     *
     * <p>The book mutation mirrors that same split: the uncrossed remainder is placed on
     * the order's own side, and the crossed volume is walked off the opposite side, bounded
     * at the placement price, composed into one atomic mutation.
     */
    private void applyPlacement(@NotNull String productId,
                                @NotNull TransactionType transaction,
                                @NotNull ProfileKey key,
                                @NotNull BazaarDataOrigin.OrderPlaced origin,
                                @NotNull PriceInfo price,
                                int volume) {
        var storage = UserOrdersStorage.orders(key);

        var type = transaction.getPriceType();
        var oppositeType = type.opposite();

        double pricePerUnit = price.pricePerItem();
        boolean isPriceExact = price.isExact();

        var data = BazaarDataRegistry.get(productId);
        long crossed = data != null ? data.estimateCrossVolume(oppositeType, pricePerUnit, volume) : 0L;

        OrderStatus initialStatus = OrderStatus.FillState.of((int) crossed, volume, origin.confirmedAt());

        boolean isFilled = initialStatus instanceof OrderStatus.Filled;

        var slotPosition = OrdersPageLayout.computeScreenSlot(productId, transaction, pricePerUnit, origin.confirmedAt(), isFilled, storage);

        long expiresAt = origin.confirmedAt() + Order.ORDER_EXPIRY_MS;

        var attribution = ProfileFlagsStorage.isKnownCoop(key)
                ? OrderAttribution.SelfInCoop.INSTANCE
                : OrderAttribution.Self.INSTANCE;

        var order = new Order(UUID.randomUUID(), productId, transaction.getSide(),
                price, volume, (int) crossed, 0, slotPosition,
                initialStatus, origin.confirmedAt(), origin.confirmedAt(), attribution,
                Optional.of(expiresAt));

        PlayerActionUtil.notifyAll("%s — Placed: %s".formatted(origin.describe(), order.describe()), NotificationType.ORDERDATA);

        int restingVolume = (int) (volume - crossed);
        if (restingVolume > 0) {
            PlayerActionUtil.notifyAll("%s — Book place: %s %s Δ%d @ %.4f%s".formatted(
                    origin.describe(), type,
                    productId, restingVolume, pricePerUnit, isPriceExact ? "" : "~"), NotificationType.BAZAARDATA);
        }

        if (crossed > 0) {
            PlayerActionUtil.notifyAll("%s — Book walk: %s %s Δ%d crossed ≤ %.4f".formatted(
                    origin.describe(), oppositeType,
                    productId, (int) crossed, pricePerUnit), NotificationType.BAZAARDATA);
        }

        var mutation = BookMutation.place(type, pricePerUnit, restingVolume)
                .then(BookMutation.walkUpTo(oppositeType, crossed, pricePerUnit));
        var delta = new OrderDelta.Place<>(order, mutation);

        var outcome = delta.mutation().apply(delta.productId(), origin);

        var result = FillInference.applyAndSettle(
                key, List.of(outcome), origin,
                UserOrdersStorage.StorageOp.add(delta.order())
                        .then(UserOrdersStorage.StorageOp.reindexIfOffsets(null, delta.order())));

        var placed = UserOrdersStorage.findById(result.ordersOrElse(key, List.of()), delta.order().id())
                .orElse(delta.order());

        new UserOrderEvent.Placed(placed, key).post(BazaarUtils.EVENT_BUS);

        var fillEvent = delta.initialFillEvent(placed, key);
        if (fillEvent != null) fillEvent.post(BazaarUtils.EVENT_BUS);

        if (outcome.changed() || !result.changedProducts().isEmpty()) {
            new BazaarDataUpdateEvent(delta.productId(), origin).post(BazaarUtils.EVENT_BUS);
        }
    }
}
