package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Handles instant-buy and instant-sell chat messages.
 *
 * <p>Each instant deal consumes volume from the opposite side of the book.
 * The side-swap is already encoded in {@link OrderInfo} by the chat handler
 * ({@link OrderInfo#fromInstantBuy} sets SELL, {@link OrderInfo#fromInstantSell}
 * sets BUY), so this source reads {@code info.getTransaction().getSide()} directly
 * as the consumed side without any additional inversion.
 * </p>
 */
@DataSource
public final class InstantDealDataSource extends BUListener {

    public InstantDealDataSource() {}

    @Subscription
    public void onInstantBuy(BazaarChatEvent.InstantBuy event) {
        applyDeal(event.getOrder(), event.getReceivedAt());
    }

    @Subscription
    public void onInstantSell(BazaarChatEvent.InstantSell event) {
        applyDeal(event.getOrder(), event.getReceivedAt());
    }

    private void applyDeal(OrderInfo info, long receivedAt) {
        var source = new DataSources.InstantDeal(receivedAt);

        var data = BazaarDataRegistry.get(info.getProductId());
        if (data == null) return;

        var book = data.bookFor(info.getTransaction());
        long sold = info.getVolume();

        // Walk price levels in book order (best price first) until volume is consumed.
        for (var entry : book.entrySet()) {
            if (sold <= 0) break;
            long atLevel = entry.getValue().totalVolume();
            long consuming = Math.min(sold, atLevel);
            data.decrement(info.getTransaction().getSide(), entry.getKey(), consuming, source);
            sold -= consuming;
        }

        new BazaarDataUpdateEvent(info.getProductId(), source).post(EVENT_BUS);

        PlayerLogger.debug("%s — Instant %s %dx @ %.4f: %s".formatted(source.describe(), info.getTransaction().is(PriceType.INSTABUY) ? "BUY" : "SELL", info.getVolume(), info.getPricePerItem(), info.getProductId()), NotificationType.ORDER_LIFECYCLE);
    }
}