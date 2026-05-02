package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Walks consumed volume off the order book when an instant transaction chat message is received.
 */
@DataSource
public final class InstantDealDataSource extends ChatOrderSource {
    private static final BazaarLogger LOG = BazaarLogger.of(InstantDealDataSource.class);

    @Subscription(priority = Priority.FIRST)
    public void onInstantBuy(BazaarChatEvent.InstantBuy event) {
        // Player consumed sell offers → book side decremented is SELL.
        applyDeal(event.product, TransactionType.Side.SELL, Util.truncateNum(event.totalCoins / event.amount), event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onInstantSell(BazaarChatEvent.InstantSell event) {
        // Player consumed buy orders → book side decremented is BUY.
        applyDeal(event.product, TransactionType.Side.BUY, Util.truncateNum(event.totalCoins / event.amount), event.amount, event.receivedAt);
    }

    private void applyDeal(String product, TransactionType.Side consumedSide, double pricePerUnit, int volume, long receivedAt) {
        var productInfo = ProductInfo.fromDisplayName(product).orElse(null);
        if (productInfo == null) {
            LOG.info("Instant deal skipped — unknown product: {}", product);

            return;
        }

        var origin = new BazaarDataOrigin.InstantDeal(receivedAt);
        var transaction = TransactionType.of(consumedSide, TransactionType.Method.ORDER);

        PlayerLogger.debug("%s — Book walk: %s %s Δ%d @ %.4f".formatted(
                origin.describe(), transaction.getPriceType(),
                productInfo.getProductId(), volume, pricePerUnit), NotificationType.PRICE_DATA, LOG);

        commit(new OrderDelta.BookOnly(
                productInfo.getProductId(), BookMutation.walk(transaction, volume)), origin);
    }
}