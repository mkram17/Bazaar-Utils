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
import org.jetbrains.annotations.NotNull;
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
        applyClaim(event.product, TransactionType.BUY_ORDER, event.pricePerUnit, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferClaimed(BazaarChatEvent.SellOfferClaimed event) {
        applyClaim(event.product, TransactionType.SELL_OFFER, event.pricePerUnit, event.amount, event.receivedAt);
    }

    /** Resolves the claimed order via {@link OrderResolver#forClaim} and advances its claimed volume. */
    private void applyClaim(@NotNull String productDisplayName, @NotNull TransactionType transaction, double pricePerUnit, int volume, long receivedAt) {
        var product = ProductInfo.fromDisplayName(productDisplayName).orElse(null);
        if (product == null) {
            Util.logMessage("Claim skipped (unknown product) — name=%s".formatted(productDisplayName));

            return;
        }

        var origin = new BazaarDataOrigin.OrderClaim(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var productId = product.getProductId();

        var matched = OrderResolver.forClaim(productId, transaction, pricePerUnit, volume, storage).orElse(null);
        if (matched == null) {
            // A sell match miss with other tracked sell orders present points at a tax
            // tier misconfiguration rather than a genuine tracking gap.
            if (transaction.is(TransactionType.SELL_OFFER) && storage.stream().anyMatch(Order.forProduct(productId, TransactionType.Side.SELL))) {
                TaxContext.warnTaxMisconfiguration("Sell claim for %s matched no tracked order.".formatted(productId));
            } else {
                Util.logMessage("Claim skipped (no matching order) — product=%s type=%s".formatted(productId, transaction.getPriceType()));
            }

            return;
        }

        PlayerActionUtil.notifyAll("%s — Claim Δ+%d (unclaimed=%d): %s".formatted(
                origin.describe(), volume, matched.unclaimedFilled(), matched.describe()), NotificationType.ORDERDATA);

        OrderDelta.Update<BazaarDataOrigin.UserPositionEvent> delta = OrderDelta.Update.claim(matched, matched.withClaim(volume, origin));

        this.commit(delta, origin, key);
    }
}