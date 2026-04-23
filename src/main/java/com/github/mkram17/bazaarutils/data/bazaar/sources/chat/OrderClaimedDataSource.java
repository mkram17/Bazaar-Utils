package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ChatOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handles "Claimed …" chat messages for both buy and sell orders.
 *
 * <p>For buy claims, {@link OrderInfo#isPriceSimilarTo} matches the stored per-unit
 * price directly. For sell claims, {@link OrderMatcher#sellClaim} applies the tax
 * reversal to compare a stored pre-tax price against the post-tax amount chat reports.
 *
 * <p>Target selection priority:
 * <ol>
 *   <li>Order whose unclaimed fill covers the claimed volume.</li>
 *   <li>Order at the competitive book position.</li>
 *   <li>Best-priced order.</li>
 * </ol>
 */
@DataSource
public final class OrderClaimedDataSource extends BUListener {

    public OrderClaimedDataSource() {}

    @Subscription
    public void onBuyOrderClaimed(BazaarChatEvent.BuyOrderClaimed event) {
        applyClaim(event.getOrder(), event.getReceivedAt());
    }

    @Subscription
    public void onSellOfferClaimed(BazaarChatEvent.SellOfferClaimed event) {
        applyClaim(event.getOrder(), event.getReceivedAt());
    }

    private void applyClaim(OrderInfo info, long receivedAt) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            Util.notifyError("Failed to source order claim; Orders storage was not loaded.", new Throwable());
            return;
        }

        var source = new DataSources.OrderClaim(receivedAt);
        TransactionType.Side side = info.getTransaction().getSide();

        var data = BazaarDataRegistry.get(info.getProductId());
        if (data == null) return;

        var candidates = storage.stream()
                .filter(Order::isLive)
                .filter(Order::isClaimable)
                .filter(Order.forProduct(info.getProductId(), side))
                .filter(order -> side == TransactionType.Side.BUY
                        ? info.isPriceSimilarTo(order.pricePerItem())
                        : OrderMatcher.sellClaim(order, info))
                .toList();

        if (candidates.isEmpty()) {
            if (side == TransactionType.Side.SELL && storage.stream().anyMatch(order -> order.productId().equals(info.getProductId()) && order.side() == side)) {
                ChatOrderParser.warnTaxMisconfiguration("Sell claim for %s matched no tracked order.".formatted(info.getProductId()));
            }

            return;
        }

        Order target = candidates.stream()
                .filter(order -> OrderMatcher.coversUnclaimedFill(order, info.getVolume()))
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .filter(order -> data.positionOf(
                                TransactionType.of(side, TransactionType.Method.ORDER),
                                order.pricePerItem()) == 0)
                        .findFirst()
                        .orElseGet(() -> candidates.stream()
                                .min(side == TransactionType.Side.BUY
                                        ? Comparator.comparingDouble(Order::pricePerItem)
                                        : Comparator.comparingDouble(Order::pricePerItem).reversed())
                                .orElseThrow()));

        var claimed = UserOrdersStorage.replace(target, t -> t.withClaim(info.getVolume())).orElse(null);
        if (claimed == null) return;

        new UserOrderEvent.Claimed(claimed).post(EVENT_BUS);
        new BazaarDataUpdateEvent(info.getProductId(), source).post(EVENT_BUS);

        UserOrdersStorage.persist();

        PlayerActionUtil.notifyAll(source.describe()
                + " | " + claimed.describe(), NotificationType.BAZAARDATA);
    }
}