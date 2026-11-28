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

    @Subscription(priority = Priority.FIRST)
    public void onApiSnapshot(ApiSnapshotEvent event) {
        var origin = new BazaarDataOrigin.ApiSnapshot(event.getTimestamp());

        var outcomes = new ArrayList<BookMutation.MutationOutcome>(event.getSnapshot().size());
        var changed = new HashSet<String>();

        for (var entry : event.getSnapshot().entrySet()) {
            String productId = entry.getKey();

            var outcome = BookMutation.splice(PriceType.INSTABUY, entry.getValue().askLevels())
                    .then(BookMutation.splice(PriceType.INSTASELL, entry.getValue().bidLevels()))
                    .apply(productId, origin);

            outcomes.add(outcome);
            if (outcome.changed()) {
                changed.add(productId);
            }
        }

        changed.addAll(FillInference.settle(outcomes, origin, FillInference.Eligibility.SESSION).changedProducts());

        if (!changed.isEmpty()) {
            new BazaarDataBatchUpdateEvent(changed, origin).post(BazaarUtils.EVENT_BUS);

            PlayerActionUtil.notifyAll(
                    "%s — %d products changed".formatted(origin.describe(), changed.size()),
                    NotificationType.BAZAARDATA
            );
        }
    }
}
