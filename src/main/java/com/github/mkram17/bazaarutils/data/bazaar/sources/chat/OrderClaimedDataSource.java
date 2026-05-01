package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.chat.BazaarChatEvent;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceQuantity;
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;

@DataSource
public final class OrderClaimedDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onBuyOrderClaimed(BazaarChatEvent.BuyOrderClaimed event) {
        applyClaim(event.product, TransactionType.BUY_ORDER, event.pricePerUnit, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onSellOfferClaimed(BazaarChatEvent.SellOfferClaimed event) {
        applyClaim(event.product, TransactionType.SELL_OFFER, event.pricePerUnit, event.amount, event.receivedAt);
    }

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

        var reference = PriceQuantity.of(pricePerUnit, volume, false);

        var candidates = OrderResolver.forClaimCandidates(productId, transaction, reference, storage);
        if (candidates.isEmpty()) {
            // Not an error: the Orders screen commonly reconciles the claim before its own
            // chat message lands, leaving nothing to match. A miss says nothing about the
            // tax setting — this branch used to warn about it for any sell miss.
            Util.logMessage("Claim skipped (no candidate — already screen-reconciled, or untracked) — product=%s type=%s price=%.4f volume=%d".formatted(
                    productId, transaction.getPriceType(), pricePerUnit, volume));

            return;
        }

        var matched = OrderResolver.selectClaimTarget(candidates, reference);

        if (candidates.size() > 1) {
            Util.logMessage("%s — Claim ambiguous among %d candidates, choice: %s".formatted(
                    origin.describe(), candidates.size(), matched.describe()));
        }

        PlayerActionUtil.notifyAll("%s — Claim Δ+%d (unclaimed=%d): %s".formatted(
                origin.describe(), volume, matched.unclaimedFilled(), matched.describe()), NotificationType.ORDERDATA);

        OrderDelta.Update<BazaarDataOrigin.UserPositionEvent> delta = OrderDelta.Update.claim(matched, matched.withClaim(volume, origin));

        var outcome = delta.mutation().apply(delta.productId(), origin);

        var result = FillInference.applyAndSettle(
                key, List.of(outcome), origin,
                UserOrdersStorage.StorageOp.replace(delta.before(), ignored -> delta.after())
                        .then(UserOrdersStorage.StorageOp.reindexIfOffsets(delta.before(), delta.after())));

        var resolved = UserOrdersStorage.findById(result.ordersOrElse(key, List.of()), delta.before().id())
                .orElse(delta.after());

        delta.kind().getEvent(delta.before(), resolved, key).post(BazaarUtils.EVENT_BUS);
        new BazaarDataUpdateEvent(delta.productId(), origin).post(BazaarUtils.EVENT_BUS);
    }
}