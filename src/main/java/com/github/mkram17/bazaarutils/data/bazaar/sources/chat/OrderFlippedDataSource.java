package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.ChatOrderSource;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.data.stored.BazaarProfileFlags;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageOrderParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;
import java.util.UUID;

/**
 * Claims the matched buy order in full and inserts a new sell offer when a flip chat
 * message arrives.
 *
 * <p>The message carries the flipped volume and total expected profit, not a sell price.
 * Sell price is recovered as {@code truncate(matchedBuy.pricePerItem() + totalProfit / amount)}
 * — {@code totalProfit} is already net of Bazaar tax, so no tax factor is applied on top.
 */
@DataSource
public final class OrderFlippedDataSource extends ChatOrderSource {
    @Subscription(priority = Priority.FIRST)
    public void onOrderFlipped(BazaarChatEvent.BuyOrderFlipped event) {
        var product = ProductInfo.fromDisplayName(event.product).orElse(null);
        if (product == null) {
            Util.logMessage("Flip skipped — unknown product: %s".formatted(event.product));

            return;
        }

        var transaction = TransactionType.SELL_OFFER;

        var origin = new BazaarDataOrigin.OrderFlipped(event.receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var productId = product.getProductId();

        int flipVolume = event.amount;
        double profitPerUnit = event.totalProfit / event.amount;

        var matchedBuy = OrderResolver.forFlip(productId, flipVolume, storage).orElse(null);
        if (matchedBuy == null) {
            Util.notifyError("%s — Flip match not found — %s vol=%d profitPerUnit=%.4f".formatted(origin.describe(), productId, flipVolume, profitPerUnit), new Throwable());

            return;
        }

        double chatSellPrice = Util.truncateNum(matchedBuy.pricePerItem() + profitPerUnit);

        var reference = OrderResolver.resolveForFlip(flipVolume, chatSellPrice);

        var pricePerUnit = reference.pricePerItem();
        var isPriceExact = reference.priceIsExact();

        // A flip claims the buy order's entire unclaimed fill — nothing is left partially claimed.
        var claimedBuy = matchedBuy.withClaim(matchedBuy.unclaimedFilled(), origin);
        var slotPosition = OrdersPageLayout.computeScreenSlot(productId, transaction, pricePerUnit, origin.confirmedAt(), false, storage);

        long expiresAt = origin.confirmedAt() + PageOrderParser.ORDER_EXPIRY_MS;

        var attribution = BazaarProfileFlags.isKnownCoop(key)
                ? new OrderAttribution.SelfInCoop()
                : new OrderAttribution.Self();

        var newSell = new Order(
                UUID.randomUUID(), productId, transaction.getSide(),
                reference.priceIsExact(), reference.pricePerItem(), reference.volume(),
                0, 0, slotPosition,
                new OrderStatus.Set(), origin.confirmedAt(), origin.confirmedAt(), attribution,
                Optional.of(expiresAt));

        PlayerActionUtil.notifyAll("%s — Flipped %s %dx: buy @ %.4f → sell @ %.4f%s (Δprofit/unit=%.4f)".formatted(
                origin.describe(), productId, flipVolume,
                matchedBuy.pricePerItem(), pricePerUnit, isPriceExact ? "" : "~", profitPerUnit), NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s — Book place: %s %s Δ%d @ %.4f (flip)".formatted(
                origin.describe(),
                transaction.getPriceType(),
                productId, flipVolume, pricePerUnit), NotificationType.BAZAARDATA);

        var mutation = BookMutation.place(transaction, reference.pricePerItem(), reference.volume());
        var delta = new OrderDelta.Swap<>(matchedBuy, claimedBuy, newSell, mutation, profitPerUnit);

        this.commit(delta, origin, key);
    }
}