package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.CurrentTransactionData;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ChatOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handles buy/sell order creation chat messages.
 *
 * <p>Inserts a new {@link Order}, applies an optimistic volume increment to the
 * book, and fires a batch update. The order is fully reindexed so its slot
 * position is immediately valid for subsequent screen reconciliation.
 */
@DataSource
public final class OrderPlacedDataSource extends BUListener {
    public OrderPlacedDataSource() {}

    @Subscription
    public void onBuyOrderCreated(BazaarChatEvent.BuyOrderCreated event) {
        applyPlacement(CurrentTransactionData.consume().orElseGet(event::getOrder), event.getReceivedAt());
    }

    @Subscription
    public void onSellOfferCreated(BazaarChatEvent.SellOfferCreated event) {
        applyPlacement(CurrentTransactionData.consume().orElseGet(event::getOrder), event.getReceivedAt());
    }

    private void applyPlacement(OrderInfo info, long receivedAt) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            PlayerLogger.sendError("Order placement skipped — profile storage not loaded", new Throwable());

            return;
        }

        var source = new DataSources.OrderPlaced(receivedAt);
        TransactionType.Side side = info.getTransaction().getSide();

        if (side == TransactionType.Side.SELL && !Util.isValidHypixelPrice(info.getPricePerItem())) {
            ChatOrderParser.warnTaxMisconfiguration("Sell order price recovery produced an invalid value (%.5f).".formatted(info.getPricePerItem()));

            return;
        }

        var data = BazaarDataRegistry.getOrCreate(info.getProductId());

        var placed = new Order(
                UUID.randomUUID(), info.getProductId(), side,
                info.getPricePerItem(), info.getVolume(), 0, 0, Order.UNANCHORED,
                new OrderStatus.Set(), source.confirmedAt(), source.confirmedAt());

        data.place(side, info.getPricePerItem(), info.getVolume(), source);

        var withNew = Stream.concat(storage.stream(), Stream.of(placed)).collect(Collectors.toCollection(ArrayList::new));
        var reindexed = OrdersPageLayout.reindexActive(withNew);
        var reindexedPlaced = UserOrdersStorage.findAfterReindex(reindexed, placed);

        UserOrdersStorage.INSTANCE.set(reindexed);
        UserOrdersStorage.persist();

        new UserOrderEvent.Placed(reindexedPlaced).post(EVENT_BUS);
        new BazaarDataUpdateEvent(info.getProductId(), source).post(EVENT_BUS);

        PlayerLogger.debug("%s — Placed: %s".formatted(source.describe(), reindexedPlaced.describe()), NotificationType.ORDER_LIFECYCLE);
    }
}