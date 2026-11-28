package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver;
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
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
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

        var origin = new BazaarDataOrigin.OrderClaimed(receivedAt);

        var key = ProfileKey.requireProfile(origin.describe()); if (key == null) return;
        var storage = UserOrdersStorage.orders(key);

        var productId = product.getProductId();
        var price = PriceInfo.exact(pricePerUnit);

        var candidates = OrderResolver.forClaimCandidates(productId, transaction, price, volume, storage);
        if (candidates.isEmpty()) {
            Util.logMessage("Claim skipped (no candidate — already screen-reconciled, or untracked) — product=%s type=%s price=%.4f volume=%d".formatted(
                    productId, transaction.getPriceType(), pricePerUnit, volume));
            return;
        }

        var matched = OrderResolver.selectClaimTarget(candidates, price, volume);

        if (candidates.size() > 1) {
            Util.logMessage("%s — Claim ambiguous among %d candidates, choice: %s".formatted(
                    origin.describe(), candidates.size(), matched.describe()));
        }

        // If the claim reveals fill volume not yet recorded on matched (e.g. player claimed
        // before Orders screen or FillInference observed the fill), advance fill first so
        // claimedAmount is never silently clamped downward and the book is decremented accurately.
        int targetClaimed = Math.min(matched.originalAmount(), matched.claimedAmount() + volume);
        int fillGap = Math.max(0, targetClaimed - matched.filledAmount());

        PlayerActionUtil.notifyAll("%s — Claim Δ+%d (unclaimed=%d%s): %s".formatted(
                origin.describe(), volume, matched.unclaimedFilled(),
                fillGap > 0 ? ", inferred fillGap=+" + fillGap : "",
                matched.describe()), NotificationType.ORDERDATA);

        if (fillGap > 0) {
            var filled = matched.withFill(fillGap, origin);
            var claimed = filled.withClaim(volume, origin);

            BookMutation<BazaarDataOrigin.OrderClaimed> mutation = matched.isOpen()
                    ? BookMutation.filled(matched.id(), transaction, matched.pricePerItem(), fillGap, filled.isFilled())
                    : BookMutation.none();

            var outcome = mutation.apply(productId, origin);

            var result = FillInference.applyAndSettle(
                    key, List.of(outcome), origin,
                    UserOrdersStorage.StorageOp.replace(matched, ignored -> claimed)
                            .then(UserOrdersStorage.StorageOp.reindexIfOffsets(matched, claimed)));

            var resolved = UserOrdersStorage.findById(result.ordersOrElse(key, List.of()), matched.id())
                    .orElse(claimed);

            OrderDelta.Update.UpdateKind.FILL.getEvent(matched, resolved, key).post(BazaarUtils.EVENT_BUS);
            OrderDelta.Update.UpdateKind.CLAIM.getEvent(filled, resolved, key).post(BazaarUtils.EVENT_BUS);

            if (outcome.changed() || !result.changedProducts().isEmpty()) {
                new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS);
            }

            return;
        }

        // Pure claim transition — no book change, so skip FillInference and BazaarDataUpdateEvent.
        var claimed = matched.withClaim(volume, origin);
        var committed = UserOrdersStorage.apply(
                key,
                UserOrdersStorage.StorageOp.replace(matched, ignored -> claimed)
                        .then(UserOrdersStorage.StorageOp.reindexIfOffsets(matched, claimed)));

        var resolved = UserOrdersStorage.findById(committed, matched.id()).orElse(claimed);

        OrderDelta.Update.UpdateKind.CLAIM.getEvent(matched, resolved, key).post(BazaarUtils.EVENT_BUS);
    }
}
