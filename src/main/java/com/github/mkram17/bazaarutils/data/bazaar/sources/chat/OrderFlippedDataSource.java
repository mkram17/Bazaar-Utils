package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.UUID;

/**
 * Handles "Order Flipped!" chat messages.
 *
 * <p>Chat reports volume and total expected profit but not the sell price. The sell
 * price is recovered as:
 * <pre>
 *   sellPrice = truncate(matchedBuy.pricePerItem() + totalProfit / amount)
 * </pre>
 * The matched buy order is claimed, a new sell offer is placed at the recovered price,
 * and both are committed atomically via {@link OrderDelta.Swap}.
 */
@DataSource
public final class OrderFlippedDataSource extends ChatOrderSource {
    private static final BazaarLogger LOG = BazaarLogger.of(OrderFlippedDataSource.class);

    @Subscription
    public void onOrderFlipped(BazaarChatEvent.BuyOrderFlipped event) {
        var origin = new BazaarDataOrigin.OrderFlipped(event.receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.warn("Flip skipped — unknown product: {}", event.product);

            return;
        }

        String productId = product.getProductId();
        int flipVolume = event.amount;
        double profitPerUnit = event.totalProfit / event.amount;

        var matchedBuy = OrderResolver.forFlip(productId, flipVolume, storage).orElse(null);
        if (matchedBuy == null) {
            PlayerLogger.sendError("Flip match not found — %s %dx (Δprofit/unit=%.4f)".formatted(productId, flipVolume, profitPerUnit), null);

            return;
        }

        double sellPrice = Util.truncateNum(matchedBuy.pricePerItem() + profitPerUnit);
        var claimedBuy = matchedBuy.withClaim(matchedBuy.unclaimedFilled(), origin);
        var slotPosition = OrdersPageLayout.computeScreenSlot(productId, TransactionType.Side.SELL, sellPrice, origin.confirmedAt(), false, storage);

        var newSell = new Order(
                UUID.randomUUID(), productId, TransactionType.Side.SELL,
                sellPrice, flipVolume, 0, 0, slotPosition,
                new OrderStatus.Set(), origin.confirmedAt(), origin.confirmedAt(), false);

        PlayerLogger.debug("%s — Flipped %s %dx: buy @ %.4f → sell @ %.4f (Δprofit/unit=%.4f)".formatted(
                origin.describe(), productId, flipVolume,
                matchedBuy.pricePerItem(), sellPrice, profitPerUnit), NotificationType.ORDER_LIFECYCLE, LOG);

        PlayerLogger.debug("%s — Book place: %s %s Δ%d @ %.4f (flip)".formatted(
                origin.describe(),
                TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER).getPriceType(),
                productId, flipVolume, sellPrice), NotificationType.PRICE_DATA, LOG);

        commit(new OrderDelta.Swap(
                matchedBuy, claimedBuy, newSell,
                BookMutation.place(TransactionType.of(
                        TransactionType.Side.SELL, TransactionType.Method.ORDER), sellPrice, flipVolume),
                profitPerUnit), origin);
    }
}