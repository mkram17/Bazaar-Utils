package com.github.mkram17.bazaarutils.data.bazaar.sources.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.BookMutation;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.*;

@DataSource
public final class ApiSnapshotDataSource extends BUListener {
    public ApiSnapshotDataSource() {}

    private record SplicedProduct(String productId, BookMutation.MutationOutcome outcome) {}

    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());

        var spliced = new ArrayList<SplicedProduct>(event.getSnapshot().size());

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();

            var outcome = BookMutation.splice(PriceType.INSTABUY, entry.getValue().asksLevels())
                    .then(BookMutation.splice(PriceType.INSTASELL, entry.getValue().bidsLevels()))
                    .apply(productId, origin);

            spliced.add(new SplicedProduct(productId, outcome));
        }

        var changed = new HashSet<String>();
        for (var product : spliced) {
            if (product.outcome().changed()) {
                changed.add(product.productId());
            }
        }
        var outcomes = spliced.stream().map(SplicedProduct::outcome).toList();

        changed.addAll(FillInference.settle(outcomes, origin, FillInference.Eligibility.SESSION).changedProducts());

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(Collections.unmodifiableSet(changed), origin).post(BazaarUtils.EVENT_BUS);

            PlayerActionUtil.notifyAll(
                    "%s — %d products changed".formatted(origin.describe(), changed.size()),
                    NotificationType.BAZAARDATA
            );
        }
    }
}