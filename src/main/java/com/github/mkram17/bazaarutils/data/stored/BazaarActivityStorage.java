package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityFold;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityRecord;
import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.BazaarIntegrationRegistry;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.TimeUtil;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Profile-scoped persistence for {@link BazaarActivityRecord}s.
 *
 * <p>Pruning runs on load, not in the background. Records older than
 * {@link BUConfig#activityRetentionMs()} are
 * dropped, and all registered {@link BazaarActivityIntegration.StoragePrunable}
 * components are notified so they can evict their own state (e.g. exported-UUID maps).
 *
 * <p>The minimum retention is 2 days, with a maximum of 356 days.
 */
public final class BazaarActivityStorage {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarActivityStorage.class);

    public static final ProfileStorage<List<BazaarActivityRecord>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "bazaar_activity",
            v -> Codec.list(BazaarActivityRecord.CODEC).xmap(ArrayList::new, l -> l),
            it -> pruneByRetention(it.get())
    );

    private static void pruneByRetention(List<BazaarActivityRecord> records) {
        if (records == null) return;
        long cutoff = System.currentTimeMillis() - BUConfig.activityRetentionMs();

        List<BazaarActivityRecord> pruned = records.stream()
                .filter(it -> it.recordedAt() < cutoff)
                .toList();

        if (pruned.isEmpty()) return;

        records.removeAll(pruned);
        INSTANCE.save();

        LOG.info("Pruned {} activity record(s) older than retention window", pruned.size());

        BazaarIntegrationRegistry.notify(
                BazaarActivityIntegration.StoragePrunable.class,
                integration -> integration.onActivityPruned(pruned));
    }

    // Read surface for all Bazaar activity data

    public static List<BazaarActivityRecord> all() {
        var storage = INSTANCE.get();

        return storage != null ? List.copyOf(storage) : List.of();
    }

    public static Stream<BazaarActivityRecord> stream() {
        return all().stream();
    }

    public static Stream<BazaarActivityRecord> withinCurrentMarketDay() {
        long dayStart = TimeUtil.LAST_BAZAAR_LIMIT_RESET_TIME.toInstant().toEpochMilli();

        return stream().filter(it -> it.recordedAt() >= dayStart);
    }

    public static <R> R fold(BazaarActivityFold<R> fold) {
        return fold.fold(stream());
    }

    public static <R> R foldToday(BazaarActivityFold<R> fold) {
        return fold.fold(withinCurrentMarketDay());
    }
}