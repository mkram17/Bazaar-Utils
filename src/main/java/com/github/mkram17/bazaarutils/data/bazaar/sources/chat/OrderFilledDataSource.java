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
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
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
 * Handles "Your Buy/Sell Order was filled" chat messages (whole-order completion only;
 * Hypixel does not emit this message for partial fills).
 *
 * <p>Target selection: competitive-position order first, then best-priced order.
 * Any unaccounted volume is decremented from the book before the fill is recorded.
 */
@DataSource
public final class OrderFilledDataSource extends BUListener {

    public OrderFilledDataSource() {}

    @Subscription
    public void onBuyOrderFilled(BazaarChatEvent.BuyOrderFilled event) {
        applyFill(event.getOrder(), event.getReceivedAt());
    }

    @Subscription
    public void onSellOfferFilled(BazaarChatEvent.SellOfferFilled event) {
        applyFill(event.getOrder(), event.getReceivedAt());
    }

    private void applyFill(OrderInfo info, long receivedAt) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            Util.notifyError("Failed to source order completion; Orders storage was not loaded.", new Throwable());
            return;
        }

        var source = new DataSources.OrderFilled(receivedAt);
        TransactionType.Side side = info.getTransaction().getSide();

        var data = BazaarDataRegistry.get(info.getProductId());
        if (data == null) return;

        var candidates = storage.stream()
                .filter(Order::isActive)
                .filter(order -> order.productId().equals(info.getProductId()))
                .filter(order -> order.side() == side)
                .filter(order -> OrderMatcher.filledOrder(order, info.getVolume()))
                .toList();

        if (candidates.isEmpty()) return;

        Order target = candidates.stream()
                .filter(order -> data.positionOf(
                        TransactionType.of(side, TransactionType.Method.ORDER),
                        order.pricePerItem()) == 0)
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .min(side == TransactionType.Side.BUY
                                ? Comparator.comparingDouble(Order::pricePerItem)
                                : Comparator.comparingDouble(Order::pricePerItem).reversed())
                        .orElseThrow());

        int unaccounted = target.unfilledAmount();
        if (unaccounted > 0) {
            data.decrement(side, target.pricePerItem(), unaccounted, source);
        }

        var completed = target.withFill(unaccounted);
        var withFill = storage.stream()
                .map(order -> order.id().equals(target.id()) ? completed : order)
                .collect(Collectors.toCollection(ArrayList::new));

        var reindexed = OrdersPageLayout.reindexIfFilled(withFill, completed);
        var reindexedCompleted = UserOrdersStorage.findAfterReindex(reindexed, completed);

        UserOrdersStorage.INSTANCE.set(reindexed);
        UserOrdersStorage.persist();

        new UserOrderEvent.Filled(reindexedCompleted).post(EVENT_BUS);
        new BazaarDataUpdateEvent(info.getProductId(), source).post(EVENT_BUS);

        PlayerActionUtil.notifyAll(source.describe()
                + " | " + reindexedCompleted.describe(), NotificationType.BAZAARDATA);
    }
}