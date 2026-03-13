package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.APIUtils;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public final class BazaarDataManager {

    @Getter
    private static volatile SkyBlockBazaarReply currentReply;
    @Getter
    private static volatile long lastSnapshotTs = -1;
    private static volatile long lastFetchWallClock = -1;

    private static volatile ScheduledFuture<?> scheduledTask;
    // Serializes schedule/cancel so only one pending fetch task exists at a time.
    private static final Object SCHED_LOCK = new Object();

    private static final AtomicInteger consecutiveIdenticalSnapshots = new AtomicInteger(0);
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /* Cached conversions: lowercase name -> productId */
    @Getter
    private static volatile Map<String, String> nameToProductIdCache = Map.of();
    @Setter
    private static volatile boolean conversionsLoaded = false;

    @RunOnInit
    public static void init() {
        scheduleFetch(0);
        PlayerActionUtil.notifyAll("BazaarDataManager initialized (simple fixed-interval poller). Base=" + BazaarDataSettings.BASE_INTERVAL_MS + "ms", NotificationType.BAZAARDATA);
    }

    private static void scheduleFetch(long delayMs) {
        synchronized (SCHED_LOCK) {
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false);
            }
            scheduledTask = BazaarUtils.BUExecutorService.schedule(BazaarDataManager::fetchOnceSafely, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void fetchOnceSafely() {
        try {
            fetchOnce();
        } catch (Throwable throwable) {
            Util.notifyError("Unexpected error in BazaarDataManager fetch loop", throwable);
            scheduleFailureRetry();
        }
    }

    private static void fetchOnce() {
        APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
            if (throwable != null) {
                handleFetchFailure(
                    "Fetch failure (" + throwable.getClass().getSimpleName() + "). Retry in " + BazaarDataSettings.FAILURE_RETRY_MS + "ms",
                    true
                );
                return;
            }

            if (reply == null || !reply.isSuccess()) {
                handleFetchFailure("Null/unsuccessful reply. Retry in " + BazaarDataSettings.FAILURE_RETRY_MS + "ms", true);
                return;
            }

            consecutiveFailures.set(0);
            updateFetchedProductIds(reply);

            long snapshotTs = extractLastUpdated(reply);
            if (snapshotTs <= 0) {
                handleFetchFailure("Invalid lastUpdated <= 0. Retry in " + BazaarDataSettings.FAILURE_RETRY_MS + "ms", false);
                return;
            }

            handleSnapshotResult(reply, snapshotTs);
            scheduleNextFromSnapshot(snapshotTs);
        });
    }

    private static void handleFetchFailure(String messagePrefix, boolean includeFailureCount) {
        int failureCount = includeFailureCount ? consecutiveFailures.incrementAndGet() : consecutiveFailures.get();
        String message = includeFailureCount ? messagePrefix + " (failures=" + failureCount + ")" : messagePrefix;
        PlayerActionUtil.notifyAll(message, NotificationType.BAZAARDATA);
        scheduleFailureRetry();
    }

    private static void scheduleFailureRetry() {
        scheduleFetch(BazaarDataSettings.FAILURE_RETRY_MS);
    }

    private static void handleSnapshotResult(SkyBlockBazaarReply reply, long snapshotTs) {
        if (snapshotTs != lastSnapshotTs) {
            handleNewSnapshot(reply, snapshotTs);
            return;
        }

        handleUnchangedSnapshot(snapshotTs);
    }

    private static void handleNewSnapshot(SkyBlockBazaarReply reply, long snapshotTs) {
        long previousSnapshotTs = lastSnapshotTs;
        lastSnapshotTs = snapshotTs;
        currentReply = reply;
        consecutiveIdenticalSnapshots.set(0);

        EVENT_BUS.post(new BazaarDataUpdateEvent(reply));

        if (previousSnapshotTs != -1) {
            PlayerActionUtil.notifyAll(
                "New snapshot " + snapshotTs + " (Δ " + (snapshotTs - previousSnapshotTs) + " ms). Scheduling next predicted fetch.",
                NotificationType.BAZAARDATA
            );
            return;
        }

        PlayerActionUtil.notifyAll("First snapshot " + snapshotTs + " received.", NotificationType.BAZAARDATA);
    }

    private static void handleUnchangedSnapshot(long snapshotTs) {
        int identicalSnapshotCount = consecutiveIdenticalSnapshots.incrementAndGet();
        PlayerActionUtil.notifyAll("Snapshot unchanged (" + snapshotTs + ") x" + identicalSnapshotCount, NotificationType.BAZAARDATA);

        if (identicalSnapshotCount == BazaarDataSettings.STALE_WARNING_THRESHOLD) {
            PlayerActionUtil.notifyAll(
                "WARNING: " + identicalSnapshotCount + " identical snapshots in a row. Server might be lagging or BASE_INTERVAL_MS too short.",
                NotificationType.BAZAARDATA
            );
        }
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long nowMs = System.currentTimeMillis();
        long expectedNextFetchAtMs = snapshotTs + BazaarDataSettings.BASE_INTERVAL_MS + BazaarDataSettings.POST_OFFSET_MS;

        long nextDelayMs;
        if (nowMs >= expectedNextFetchAtMs) {
            // Past the ideal fetch time; server has not advanced snapshot yet, so back off.
            nextDelayMs = BazaarDataSettings.STALE_BACKOFF_MS;
        } else {
            long idealDelayMs = expectedNextFetchAtMs - nowMs;
            nextDelayMs = Math.max(idealDelayMs, BazaarDataSettings.STALE_BACKOFF_MS);
        }

        scheduleFetch(nextDelayMs);
    }

    private static long extractLastUpdated(SkyBlockBazaarReply reply) {
        try {
            return ((AccessorSkyBlockBazaarReply) reply).getLastUpdated();
        } catch (Exception e) {
            Util.notifyError("Failed to access lastUpdated (mixin+reflection failed)", e);
            return -1;
        }
    }


    /**
     * Cached conversion load. Thread-safe (single pass).
     */
    protected static void ensureConversionsLoaded() {
        if (conversionsLoaded) {
            return;
        }

        // Double-checked guard avoids repeated JSON parsing on the hot path.
        synchronized (BazaarDataManager.class) {
            if (conversionsLoaded) {
                return;
            }

            try {
                Map<String, String> mutable = new HashMap<>();

                var resources = ResourceManager.getResourceJson();
                var conversions = resources.getAsJsonObject();

                for (String key : conversions.keySet()) {
                    String value = conversions.get(key).getAsString();
                    if (value != null) {
                        mutable.put(value.toLowerCase(Locale.ROOT), key);
                    }
                }

                nameToProductIdCache = Collections.unmodifiableMap(mutable);
                conversionsLoaded = true;

                PlayerActionUtil.notifyAll("Loaded bazaarConversions cache: " + nameToProductIdCache.size() + " entries.", NotificationType.BAZAARDATA);
            } catch (Exception e) {
                Util.notifyError("Failed loading bazaarConversions cache", e);

                nameToProductIdCache = Map.of();
                conversionsLoaded = true;
            }
        }
    }
}