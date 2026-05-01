package com.github.mkram17.bazaarutils.data.bazaar.sources.chat;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderDelta;
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
import org.jetbrains.annotations.NotNull;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.List;

@DataSource
public final class InstantDealDataSource extends BUListener {
    @Subscription(priority = Priority.FIRST)
    public void onInstantBuy(BazaarChatEvent.InstantBuy event) {
        var pricePerUnit = Util.truncateNum(event.totalCoins / event.amount);

        applyDeal(event.product, TransactionType.INSTANT_BUY, pricePerUnit, event.amount, event.receivedAt);
    }

    @Subscription(priority = Priority.FIRST)
    public void onInstantSell(BazaarChatEvent.InstantSell event) {
        var pricePerUnit = Util.truncateNum(event.totalCoins / event.amount);

        applyDeal(event.product, TransactionType.INSTANT_SELL, pricePerUnit, event.amount, event.receivedAt);
    }

    private void applyDeal(@NotNull String productDisplayName, @NotNull TransactionType transaction, double pricePerUnit, int volume, long receivedAt) {
        var product = ProductInfo.fromDisplayName(productDisplayName).orElse(null);
        if (product == null) {
            Util.logMessage("Instant deal skipped (unknown product) — name=%s".formatted(productDisplayName));

            return;
        }

        var origin = new BazaarDataOrigin.InstantDeal(receivedAt);

        var productId = product.getProductId();

        PlayerActionUtil.notifyAll("%s — Book walk: %s %s Δ%d @ %.4f".formatted(
                origin.describe(), transaction.getPriceType(),
                productId, volume, pricePerUnit), NotificationType.BAZAARDATA);

        var mutation = BookMutation.walk(transaction, volume);
        var delta = new OrderDelta.BookOnly<>(productId, mutation);

        var outcome = delta.mutation().apply(delta.productId(), origin);

        FillInference.settle(List.of(outcome), origin, FillInference.Eligibility.ALWAYS);

        new BazaarDataUpdateEvent(delta.productId(), origin).post(BazaarUtils.EVENT_BUS);
    }
}