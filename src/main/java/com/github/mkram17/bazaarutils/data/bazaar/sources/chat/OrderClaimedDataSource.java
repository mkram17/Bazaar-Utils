package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Advances {@code claimedAmount} on the matched order when a claim chat message is received.
 */
@DataSource
public final class OrderClaimedDataSource extends ChatOrderSource {
    private static final BazaarLogger LOG = BazaarLogger.of(OrderClaimedDataSource.class);

    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderClaimed(BazaarChatEvent.BuyOrderClaimed event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.info("Claim skipped (unknown product) — name={}", event.product);

            return;
        }

        applyClaim(product.getProductId(), TransactionType.Side.BUY, event.pricePerUnit, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferClaimed(BazaarChatEvent.SellOfferClaimed event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            LOG.info("Claim skipped (unknown product) — name={}", event.product);

            return;
        }

        applyClaim(product.getProductId(), TransactionType.Side.SELL, event.pricePerUnit, event.amount, event.receivedAt);
    }

    /**
     * Resolves the target order via {@link OrderResolver#forClaim} and commits the claim delta.
     */
    private void applyClaim(String productId, TransactionType.Side side, double pricePerUnit, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderClaim(receivedAt);
        var storage = requireStorage(origin); if (storage == null) return;

        var matched = OrderResolver.forClaim(productId, side, pricePerUnit, volume, storage).orElse(null);
        if (matched == null) {
            // Other sell orders exist, so the match should have succeeded — the miss is
            // more likely a BazaarFlipper tier mismatch than a tracking gap.
            if (side == TransactionType.Side.SELL && storage.stream().anyMatch(Order.forProduct(productId, TransactionType.Side.SELL))) {
                TaxContext.warnTaxMisconfiguration("Sell claim for %s matched no tracked order.".formatted(productId));
            } else {
                LOG.warn("Claim skipped — no matching order: product={} side={} price={} vol={}", productId, side, pricePerUnit, volume);
            }

            return;
        }

        PlayerLogger.debug("%s — Claim Δ+%d on %s %s @ %.4f (unclaimed=%d)".formatted(
                origin.describe(), volume, side, productId, pricePerUnit,
                matched.unclaimedFilled()), NotificationType.ORDER_LIFECYCLE, LOG);

        commit(OrderDelta.Update.claim(matched, matched.withClaim(volume, origin)), origin);
    }
}