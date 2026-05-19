package com.github.mkram17.bazaarutils.data.bazaar.activity;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The sole writer to {@link BazaarActivityStorage}.
 *
 * <p>Subscribes to {@link com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent}
 * subtypes for lifecycle orders and directly to
 * {@link com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent.InstantBuy} /
 * {@link com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent.InstantSell}
 * for instant deals — instant deals commit {@code OrderDelta.BookOnly} and produce no
 * {@code UserOrderEvent}.
 *
 * <p>All subscriptions use {@link Priority#LOW} so data sources commit to
 * {@link com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage} before this
 * recorder observes the resulting events.
 *
 * <h2>onPlaced — screen-synthesized orders</h2>
 * {@code UserOrderEvent.Placed} fires for both chat-detected placements and orders
 * synthesized by {@link com.github.mkram17.bazaarutils.data.bazaar.sources.gui.OrdersScreenDataSource}.
 * Screen-synthesized orders may already have non-zero {@code filledAmount} and
 * {@code claimedAmount} — the recorder writes these through rather than hardcoding zeros.
 *
 * <h2>onClaimed — fires on every increment, not just terminal claim</h2>
 * {@code UserOrderEvent.Claimed} fires whenever {@code claimedAmount} advances,
 * including partial claims mid-lifecycle. The activity record's {@code status} is
 * updated to whatever {@link com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order#status()}
 * is at that moment — {@link OrderStatus.Claimed} only when all filled volume is claimed
 * and the order is terminal.
 *
 * <h2>onFlipped</h2>
 * A flip claims the remaining filled volume on the buy order and immediately creates a
 * sell offer. The recorder updates the buy's {@link BazaarActivityRecord.BuyOrderActivity}
 * with the newly flip-claimed volume, then appends a {@link BazaarActivityRecord.FlipSellActivity}
 * carrying both {@code sourceId} (link to the buy record, for integrations) and
 * {@code sourcePricePerItem} (cost basis captured at flip time, self-contained).
 */
@Module
public final class BazaarActivityRecorder extends BUListener {

    private static final BazaarLogger LOG = BazaarLogger.of(BazaarActivityRecorder.class);

    @Subscription(priority = Priority.LOW)
    public void onPlaced(UserOrderEvent.Placed event) {
        var order = event.getOrder();

        append(switch (order.side()) {
            case BUY -> new BazaarActivityRecord.BuyOrderActivity(
                    order.id(), order.productId(), order.pricePerItem(),
                    order.originalAmount(), order.filledAmount(), order.claimedAmount(), 0,
                    order.placedAt(), order.status(), order.coopOrder());
            case SELL -> new BazaarActivityRecord.SellOfferActivity(
                    order.id(), order.productId(), order.pricePerItem(),
                    order.originalAmount(), order.filledAmount(), order.claimedAmount(),
                    order.placedAt(), order.status(), order.coopOrder());
        });
    }

    @Subscription(priority = Priority.LOW)
    public void onPartiallyFilled(UserOrderEvent.PartiallyFilled event) {
        var order = event.getOrder();

        mutate(order.id(), record -> switch (record) {
            case BazaarActivityRecord.BuyOrderActivity buy -> new BazaarActivityRecord.BuyOrderActivity(
                    buy.id(), buy.productId(), buy.pricePerItem(), buy.originalAmount(),
                    order.filledAmount(), buy.claimedAmount(), buy.flippedAmount(),
                    buy.placedAt(), order.status(), buy.coopOrder());
            case BazaarActivityRecord.SellOfferActivity sell -> new BazaarActivityRecord.SellOfferActivity(
                    sell.id(), sell.productId(), sell.pricePerItem(), sell.originalAmount(),
                    order.filledAmount(), sell.claimedAmount(),
                    sell.placedAt(), order.status(), sell.coopOrder());
            case BazaarActivityRecord.FlipSellActivity flip -> new BazaarActivityRecord.FlipSellActivity(
                    flip.id(), flip.sourceId(), flip.sourcePricePerItem(), flip.productId(), flip.pricePerItem(),
                    flip.originalAmount(), order.filledAmount(), flip.claimedAmount(),
                    flip.placedAt(), order.status(), flip.coopOrder());
            default -> record;
        });
    }

    @Subscription(priority = Priority.LOW)
    public void onFilled(UserOrderEvent.Filled event) {
        var order = event.getOrder();

        mutate(order.id(), record -> switch (record) {
            case BazaarActivityRecord.BuyOrderActivity buy -> new BazaarActivityRecord.BuyOrderActivity(
                    buy.id(), buy.productId(), buy.pricePerItem(), buy.originalAmount(),
                    order.filledAmount(), buy.claimedAmount(), buy.flippedAmount(),
                    buy.placedAt(), order.status(), buy.coopOrder());
            case BazaarActivityRecord.SellOfferActivity sell -> new BazaarActivityRecord.SellOfferActivity(
                    sell.id(), sell.productId(), sell.pricePerItem(), sell.originalAmount(),
                    order.filledAmount(), sell.claimedAmount(),
                    sell.placedAt(), order.status(), sell.coopOrder());
            case BazaarActivityRecord.FlipSellActivity flip -> new BazaarActivityRecord.FlipSellActivity(
                    flip.id(), flip.sourceId(), flip.sourcePricePerItem(), flip.productId(), flip.pricePerItem(),
                    flip.originalAmount(), order.filledAmount(), flip.claimedAmount(),
                    flip.placedAt(), order.status(), flip.coopOrder());
            default -> record;
        });
    }


    /**
     * Fires for every claim increment.
     * {@code claimedAt} is only set when {@link OrderStatus.Claimed} is reached
     * — i.e. all filled volume has been claimed and the order no longer exists.
     */
    @Subscription(priority = Priority.LOW)
    public void onClaimed(UserOrderEvent.Claimed event) {
        var order = event.getOrder();

        Long claimedAt = order.status() instanceof OrderStatus.Claimed(long at) ? at : null;

        mutate(order.id(), record -> switch (record) {
            case BazaarActivityRecord.BuyOrderActivity buy -> new BazaarActivityRecord.BuyOrderActivity(
                    buy.id(), buy.productId(), buy.pricePerItem(), buy.originalAmount(),
                    buy.filledAmount(), order.claimedAmount(), buy.flippedAmount(),
                    buy.placedAt(), order.status(), buy.coopOrder());
            case BazaarActivityRecord.SellOfferActivity sell -> new BazaarActivityRecord.SellOfferActivity(
                    sell.id(), sell.productId(), sell.pricePerItem(), sell.originalAmount(),
                    sell.filledAmount(), order.claimedAmount(),
                    sell.placedAt(), order.status(), sell.coopOrder());
            case BazaarActivityRecord.FlipSellActivity flip -> new BazaarActivityRecord.FlipSellActivity(
                    flip.id(), flip.sourceId(), flip.sourcePricePerItem(), flip.productId(), flip.pricePerItem(),
                    flip.originalAmount(), flip.filledAmount(), order.claimedAmount(),
                    flip.placedAt(), order.status(), flip.coopOrder());
            default -> record;
        });
    }

    @Subscription(priority = Priority.LOW)
    public void onCancelled(UserOrderEvent.Cancelled event) {
        var order = event.getOrder();

        mutate(order.id(), record -> switch (record) {
            case BazaarActivityRecord.BuyOrderActivity buy -> new BazaarActivityRecord.BuyOrderActivity(
                    buy.id(), buy.productId(), buy.pricePerItem(), buy.originalAmount(),
                    buy.filledAmount(), buy.claimedAmount(), buy.flippedAmount(),
                    buy.placedAt(), order.status(), buy.coopOrder());
            case BazaarActivityRecord.SellOfferActivity sell -> new BazaarActivityRecord.SellOfferActivity(
                    sell.id(), sell.productId(), sell.pricePerItem(), sell.originalAmount(),
                    sell.filledAmount(), sell.claimedAmount(),
                    sell.placedAt(), order.status(), sell.coopOrder());
            case BazaarActivityRecord.FlipSellActivity flip -> new BazaarActivityRecord.FlipSellActivity(
                    flip.id(), flip.sourceId(), flip.sourcePricePerItem(), flip.productId(), flip.pricePerItem(),
                    flip.originalAmount(), flip.filledAmount(), flip.claimedAmount(),
                    flip.placedAt(), order.status(), flip.coopOrder());
            default -> record;
        });
    }

    @Subscription(priority = Priority.LOW)
    public void onFlipped(UserOrderEvent.Flipped event) {
        var claimedBuy = event.getOrder();
        var flipped = event.getNewOrder();

        mutate(claimedBuy.id(), record -> switch (record) {
            case BazaarActivityRecord.BuyOrderActivity buy -> {
                int newlyFlipClaimed = claimedBuy.claimedAmount() - buy.claimedAmount();

                yield new BazaarActivityRecord.BuyOrderActivity(
                        buy.id(), buy.productId(), buy.pricePerItem(), buy.originalAmount(),
                        buy.filledAmount(), claimedBuy.claimedAmount(),
                        buy.flippedAmount() + newlyFlipClaimed,
                        buy.placedAt(), claimedBuy.status(), buy.coopOrder());
            }
            default -> record;
        });


        append(new BazaarActivityRecord.FlipSellActivity(
                flipped.id(), claimedBuy.id(), claimedBuy.pricePerItem(), flipped.productId(), flipped.pricePerItem(),
                flipped.originalAmount(), 0, 0,
                flipped.placedAt(), new OrderStatus.Set(), flipped.coopOrder()));
    }


    @Subscription(priority = Priority.LOW)
    public void onInstantBuy(BazaarChatEvent.InstantBuy event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) { LOG.warn("InstantBuy activity dropped (unknown product) — name={}", event.product); return; }
        append(new BazaarActivityRecord.InstantBuy(
                UUID.randomUUID(), product.getProductId(),
                Util.truncateNum(event.totalCoins / event.amount), event.amount, event.receivedAt));
    }

    @Subscription(priority = Priority.LOW)
    public void onInstantSell(BazaarChatEvent.InstantSell event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) { LOG.warn("InstantSell activity dropped (unknown product) — name={}", event.product); return; }
        append(new BazaarActivityRecord.InstantSell(
                UUID.randomUUID(), product.getProductId(),
                Util.truncateNum(event.totalCoins / event.amount), event.amount, event.receivedAt));
    }
    private static void append(BazaarActivityRecord record) {
        if (BazaarActivityStorage.INSTANCE.get() == null) {
            PlayerLogger.sendError("Activity record dropped — storage not loaded for " + record.id(), null);

            return;
        }

        BazaarActivityStorage.INSTANCE.edit(list -> list.add(record));
    }

    private static void mutate(UUID id, UnaryOperator<BazaarActivityRecord> operator) {
        var storage = BazaarActivityStorage.INSTANCE.get();
        if (storage == null) return;

        boolean[] found = {false};
        var updated = storage.stream()
                .map(record -> {
                    if (!record.id().equals(id)) return record;
                    found[0] = true;
                    return operator.apply(record);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        if (!found[0]) {
            LOG.warn("BazaarActivityRecorder.mutate: no record found for {}", id);

            return;
        }

        BazaarActivityStorage.INSTANCE.set(updated);
    }
}