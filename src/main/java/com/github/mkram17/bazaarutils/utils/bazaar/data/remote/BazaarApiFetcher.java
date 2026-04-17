package com.github.mkram17.bazaarutils.utils.bazaar.data.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarApiSnapshotEvent;
import com.github.mkram17.bazaarutils.utils.APIUtil;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public final class BazaarApiFetcher {
    private static final BazaarApiFetcherSettings FETCH_SETTINGS = new BazaarApiFetcherSettings();

    // Serializes schedule/cancel so only one pending fetch task exists at a time.
    private static final Object SCHED_LOCK = new Object();
    private static volatile ScheduledFuture<?> scheduledTask;

    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final AtomicInteger consecutiveIdenticalSnapshots = new AtomicInteger(0);

    @Getter
    private static volatile long lastSnapshotTs = -1;

    @RunOnInit
    public static void init() {
        scheduleFetch(0);
        PlayerActionUtil.notifyAll("BazaarApiFetcher initialized (simple fixed-interval poller). Base=" + FETCH_SETTINGS.BASE_INTERVAL_MS + "ms", NotificationType.BAZAARDATA);
    }

    private static void scheduleFetch(long delayMs) {
        synchronized (SCHED_LOCK) {
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false);
            }

            scheduledTask = BazaarUtils.BUExecutorService.schedule(BazaarApiFetcher::fetchOnceSafely, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void fetchOnceSafely() {
        try {
            fetchOnce();
        } catch (Throwable throwable) {
            Util.notifyError("Unexpected error in BazaarApiFetcher fetch loop", throwable);
            scheduleFailureRetry();
        }
    }

    private static void fetchOnce() {
        APIUtil.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
            try {
                if (throwable != null) {
                    handleFetchFailure(
                        "Fetch failure (" + throwable.getClass().getSimpleName() + "). Retry in " + FETCH_SETTINGS.FAILURE_RETRY_MS + "ms",
                        throwable,
                        true
                    );
                    return;
                }

                if (reply == null || !reply.isSuccess()) {
                    handleFetchFailure(
                        "Fetch failure (Unsuccessful Reply). Retry in " + FETCH_SETTINGS.FAILURE_RETRY_MS + "ms",
                        new RuntimeException("API reply was null or not successful"),
                        true
                    );
                    return;
                }

                BazaarApiSnapshotEvent event = BazaarApiConverter.convert(reply);
                long snapshotTs = event.getTimestamp(); // or however your event exposes it

                if (snapshotTs <= 0) {
                    handleFetchFailure("Invalid lastUpdated <= 0. Retry in " + FETCH_SETTINGS.FAILURE_RETRY_MS + "ms", new RuntimeException("lastUpdated <= 0"), false);
                    return;
                }

                consecutiveFailures.set(0);

                handleSnapshotResult(event, snapshotTs);
                scheduleNextFromSnapshot(snapshotTs);
            } catch (Throwable t) {
                handleFetchFailure("Unexpected error in fetch completion. Retry in " + FETCH_SETTINGS.FAILURE_RETRY_MS + "ms", t, true);
            }
        });
    }

    private static void handleFetchFailure(String messagePrefix, Throwable cause, boolean includeFailureCount) {
        int failureCount = includeFailureCount ? consecutiveFailures.incrementAndGet() : consecutiveFailures.get();
        String message = includeFailureCount ? messagePrefix + " (failures=" + failureCount + ")" : messagePrefix;
        Util.notifyError(message, cause);
        scheduleFailureRetry();
    }

    private static void scheduleFailureRetry() {
        scheduleFetch(FETCH_SETTINGS.FAILURE_RETRY_MS);
    }

    private static void handleSnapshotResult(BazaarApiSnapshotEvent event, long snapshotTs) {
        if (snapshotTs != lastSnapshotTs) {
            handleNewSnapshot(event, snapshotTs);

            return;
        }

        handleUnchangedSnapshot(snapshotTs);
    }

    private static void handleNewSnapshot(BazaarApiSnapshotEvent event, long snapshotTs) {
        long previousSnapshotTs = lastSnapshotTs;

        lastSnapshotTs = snapshotTs;
        consecutiveIdenticalSnapshots.set(0);

        // Marshall to game thread before posting — the event bus has no thread
        // safety guarantees and all subscribers expect to run on the game thread.
        Minecraft.getInstance().execute(() -> event.post(EVENT_BUS));

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

        if (identicalSnapshotCount == FETCH_SETTINGS.STALE_WARNING_THRESHOLD) {
            PlayerActionUtil.notifyAll(
                "WARNING: " + identicalSnapshotCount + " identical snapshots in a row. Server might be lagging or BASE_INTERVAL_MS too short.",
                NotificationType.BAZAARDATA
            );
        }
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long nowMs = System.currentTimeMillis();
        long expectedNextFetchAtMs = snapshotTs + FETCH_SETTINGS.BASE_INTERVAL_MS + FETCH_SETTINGS.POST_OFFSET_MS;

        long nextDelayMs;
        if (nowMs >= expectedNextFetchAtMs) {
            // Past the ideal fetch time; server has not advanced snapshot yet, so back off.
            nextDelayMs = FETCH_SETTINGS.STALE_BACKOFF_MS;
        } else {
            long idealDelayMs = expectedNextFetchAtMs - nowMs;
            nextDelayMs = Math.max(idealDelayMs, FETCH_SETTINGS.STALE_BACKOFF_MS);
        }

        scheduleFetch(nextDelayMs);
    }
}