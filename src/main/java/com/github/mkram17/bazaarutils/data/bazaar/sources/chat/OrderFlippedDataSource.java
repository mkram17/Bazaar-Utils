package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.CurrentOrderData;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handles "Order Flipped!" chat messages.
 *
 * <p>Chat carries volume and total profit but not the actual sell price. The sell
 * price is recovered from the matching filled BUY order in storage:
 * <pre>
 *   sellPrice = buyPricePerItem + profitPerUnit
 * </pre>
 * where {@code profitPerUnit} is what {@link com.github.mkram17.bazaarutils.utils.bazaar.components.ChatOrderParser#parseFlipped}
 * stored in {@code info.getPricePerItem()}.
 *
 * <p>The matched BUY order is claimed for the flipped volume, a new SELL order is
 * placed at the recovered price, and both are reindexed.
 */
@DataSource
public final class OrderFlippedDataSource extends BUListener {

    public OrderFlippedDataSource() {}

    @Subscription
    public void onOrderFlipped(BazaarChatEvent.BuyOrderFlipped event) {
        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage == null) {
            Util.notifyError("Failed to source order flip; Orders storage was not loaded.", new Throwable());
            return;
        }

        OrderInfo info = event.getOrder();
        var source = new DataSources.OrderFlipped(event.receivedAt);
        int flipVolume = info.getVolume();
        // info.getPricePerItem() carries profit-per-unit, as set by ChatOrderParser.parseFlipped.
        double profitPerUnit = info.getPricePerItem();

        var data = BazaarDataRegistry.get(info.getProductId());
        if (data == null) return;

        var matchedBuy = CurrentOrderData.getForOptions()
                .filter(Order::isFlippable)
                .filter(Order.forProduct(info.getProductId(), TransactionType.Side.BUY))
                .or(() -> storage.stream()
                        .filter(Order.forProduct(info.getProductId(), TransactionType.Side.BUY))
                        .filter(Order::isFlippable)
                        .filter(order -> OrderMatcher.coversUnclaimedFill(order, flipVolume))
                        .min(Comparator.comparingInt(order -> order.unclaimedFilled() - flipVolume)))
                .orElse(null);

        if (matchedBuy == null) {
            Util.notifyError("Failed to find matching buy order | "
                    + "productId=" + info.getProductId()
                    + ", flippedVolume=" + flipVolume
                    + ", profitPerUnit=" + profitPerUnit, new Throwable());

            return;
        }

        double sellPrice = Util.truncateNum(matchedBuy.pricePerItem() + profitPerUnit);

        var flipped = new Order(
                UUID.randomUUID(), info.getProductId(), TransactionType.Side.SELL,
                sellPrice, flipVolume, 0, 0, Order.UNANCHORED,
                new OrderStatus.Set(), source.confirmedAt(), source.confirmedAt());

        data.place(TransactionType.Side.SELL, sellPrice, flipVolume, source);

        var claimedBuy = matchedBuy.withClaim(matchedBuy.unclaimedFilled());
        var withFlip = Stream.concat(
                        storage.stream().map(order -> order.id().equals(matchedBuy.id()) ? claimedBuy : order),
                        Stream.of(flipped))
                .collect(Collectors.toCollection(ArrayList::new));

        var reindexed = OrdersPageLayout.reindexActive(withFlip);
        var reindexedFlipped = UserOrdersStorage.findAfterReindex(reindexed, flipped);
        var reindexedClaimedBuy = UserOrdersStorage.findAfterReindex(reindexed, claimedBuy);

        UserOrdersStorage.INSTANCE.set(reindexed);
        UserOrdersStorage.persist();

        new UserOrderEvent.Flipped(reindexedClaimedBuy, reindexedFlipped).post(EVENT_BUS);
        new BazaarDataUpdateEvent(info.getProductId(), source).post(EVENT_BUS);

        PlayerActionUtil.notifyAll(source.describe()
                + " | " + reindexedFlipped.describe(), NotificationType.BAZAARDATA);
    }
}