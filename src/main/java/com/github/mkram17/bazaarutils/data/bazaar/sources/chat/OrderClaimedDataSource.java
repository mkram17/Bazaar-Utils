package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TaxContext;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

/**
 * Advances {@code claimedAmount} on the matched order when a buy or sell claim message
 * arrives.
 *
 * <p>Both message types carry the pre-tax listed price per unit, so the match against
 * the stored order's own price needs no tax reversal.
 */
@DataSource
public final class OrderClaimedDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderClaimed(BazaarChatEvent.BuyOrderClaimed event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Claim skipped (unknown product) — name=%s".formatted(event.product));

            return;
        }

        applyClaim(product.getProductId(), TransactionType.Side.BUY, event.pricePerUnit, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferClaimed(BazaarChatEvent.SellOfferClaimed event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Claim skipped (unknown product) — name=%s".formatted(event.product));

            return;
        }

        applyClaim(product.getProductId(), TransactionType.Side.SELL, event.pricePerUnit, event.amount, event.receivedAt);
    }

    /**
     * Resolves the claimed order via {@link OrderResolver#forClaim} and advances its
     * claimed volume.
     */
    private void applyClaim(String productId, TransactionType.Side side, double pricePerUnit, int volume, long receivedAt) {
        var origin = new BazaarDataOrigin.OrderClaim(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var matched = OrderResolver.forClaim(productId, side, pricePerUnit, volume, storage).orElse(null);
        if (matched == null) {
            // A sell match miss with other tracked sell orders present points at a tax
            // tier misconfiguration rather than a genuine tracking gap.
            if (side == TransactionType.Side.SELL && storage.stream().anyMatch(Order.forProduct(productId, TransactionType.Side.SELL))) {
                TaxContext.warnTaxMisconfiguration("Sell claim for %s matched no tracked order.".formatted(productId));
            } else {
                Util.logMessage("Claim skipped (no matching order) — product=%s side=%s".formatted(productId, side));
            }

            return;
        }

        PlayerActionUtil.notifyAll("%s — Claim Δ+%d on %s %s @ %.4f (unclaimed=%d)".formatted(
                origin.describe(), volume, side, productId, pricePerUnit,
                matched.unclaimedFilled()), NotificationType.ORDERDATA);

        commit(OrderDelta.Update.claim(matched, matched.withClaim(volume, origin)), origin, key);
    }
}