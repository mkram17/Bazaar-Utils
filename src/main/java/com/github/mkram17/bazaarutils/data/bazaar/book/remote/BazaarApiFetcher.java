package com.github.mkram17.bazaarutils.data.bazaar.book.remote;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Polls the Hypixel Bazaar HTTP endpoint at a fixed interval and posts
 * {@link com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent} on the game thread
 * whenever a newer snapshot is received.
 *
 * <p>The next fetch is scheduled relative to the snapshot's own {@code lastUpdated} timestamp
 * ({@code lastUpdated + baseIntervalMs + postOffsetMs}) rather than wall-clock time, so
 * fetches stay aligned with Hypixel's actual update cadence rather than drifting.
 *
 * <p>At most one fetch task is outstanding at a time; {@code SCHED_LOCK} serializes
 * schedule/cancel. Consecutive failures back off to {@code failureRetryMs} and surface an
 * error to the player after {@code failureErrorThreshold} consecutive failures.
 * Consecutive identical snapshots are tracked; a warning fires at {@code staleWarningThreshold}.
 */
public final class BazaarApiFetcher {
    private BazaarApiFetcher() {}

    private static final BazaarApiFetcherSettings FETCH_SETTINGS = BazaarApiFetcherSettings.DEFAULT;

    // Serializes schedule/cancel so only one pending fetch task exists at a time.
    private static final Object SCHED_LOCK = new Object();
    private static volatile ScheduledFuture<?> scheduledTask;

    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final AtomicInteger consecutiveIdenticalSnapshots = new AtomicInteger(0);

    /** Timestamp of the most recently processed snapshot, or {@code -1} if none has been received this session. */
    @Getter
    private static volatile long lastSnapshotTs = -1;

    @RunOnInit
    public static void init() {
        scheduleFetch(0);
        Util.logMessage("BazaarApiFetcher initialized (simple fixed-interval poller) — base interval %dms, post-offset %dms"
                .formatted(FETCH_SETTINGS.baseIntervalMs(), FETCH_SETTINGS.postOffsetMs()));
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
            Util.logError("Unexpected throwable escaped fetch loop — scheduling retry", throwable);
            scheduleFailureRetry();
        }
    }

    private static void fetchOnce() {
        APIUtil.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
            try {
                if (throwable != null) {
                    handleFetchFailure(throwable.getClass().getSimpleName(), throwable, true);

                    return;
                }

                if (reply == null || !reply.isSuccess()) {
                    handleFetchFailure("unsuccessful reply", new RuntimeException("API reply was null or not successful"), true);

                    return;
                }

                ApiSnapshotEvent event = BazaarApiConverter.convert(reply);
                long snapshotTs = event.getTimestamp();

                if (snapshotTs <= 0) {
                    handleFetchFailure("lastUpdated <= 0", new RuntimeException("lastUpdated <= 0"), false);

                    return;
                }

                consecutiveFailures.set(0);

                handleSnapshotResult(event, snapshotTs);
                scheduleNextFromSnapshot(Math.max(snapshotTs, lastSnapshotTs));
            } catch (Throwable t) {
                handleFetchFailure("Unexpected error in fetch completion. Retry in " + FETCH_SETTINGS.failureRetryMs() + "ms", t, true);
            }
        });
    }

    private static void handleFetchFailure(String reason, Throwable cause, boolean countFailures) {
        int failureCount = countFailures ? consecutiveFailures.incrementAndGet() : consecutiveFailures.get();

        Util.logError("Fetch failure: %s — retry in %dms (consecutive=%d)".formatted(reason, FETCH_SETTINGS.failureRetryMs(), failureCount), cause);

        if (countFailures && failureCount >= FETCH_SETTINGS.failureErrorThreshold()) {
            Util.notifyError("API fetch has failed " + failureCount + " times in a row — price data is stale.", cause);
        }

        scheduleFailureRetry();
    }

    private static void scheduleFailureRetry() {
        scheduleFetch(FETCH_SETTINGS.failureRetryMs());
    }

    private static void handleSnapshotResult(ApiSnapshotEvent event, long snapshotTs) {
        if (snapshotTs > lastSnapshotTs) {
            handleNewSnapshot(event, snapshotTs);
            return;
        }

        if (snapshotTs == lastSnapshotTs) {
            handleUnchangedSnapshot(snapshotTs);
            return;
        }

        Util.logMessage("Ignoring out-of-order API snapshot — ts=%d < last=%d".formatted(snapshotTs, lastSnapshotTs));
    }

    private static void handleNewSnapshot(ApiSnapshotEvent event, long snapshotTs) {
        long previousSnapshotTs = lastSnapshotTs;

        lastSnapshotTs = snapshotTs;
        consecutiveIdenticalSnapshots.set(0);

        // Marshall to game thread before posting — the event bus has no thread
        // safety guarantees and all subscribers expect to run on the game thread.
        Minecraft.getInstance().execute(() -> event.post(EVENT_BUS));

        if (previousSnapshotTs == -1) {
            Util.logMessage("First API snapshot received — ts=%d".formatted(snapshotTs));

            return;
        }

        PlayerActionUtil.notifyAll("API snapshot received — ts=" + snapshotTs + " (Δ" + (snapshotTs - previousSnapshotTs) + "ms)", NotificationType.BAZAARDATA);
    }

    private static void handleUnchangedSnapshot(long snapshotTs) {
        int count = consecutiveIdenticalSnapshots.incrementAndGet();

        Util.logMessage("Snapshot unchanged — ts=%d x%d".formatted(snapshotTs, count));

        if (count == FETCH_SETTINGS.staleWarningThreshold()) {
            Util.logMessage("API stale snapshot x%d — ts=%d — server may be lagging or baseIntervalMs too short".formatted(count, snapshotTs));
        }
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long nowMs = System.currentTimeMillis();
        long expectedNextFetchAtMs = snapshotTs + FETCH_SETTINGS.baseIntervalMs() + FETCH_SETTINGS.postOffsetMs();

        long nextDelayMs;
        if (nowMs >= expectedNextFetchAtMs) {
            int staleStreak = Math.min(consecutiveIdenticalSnapshots.get(), 5);
            nextDelayMs = Math.min(FETCH_SETTINGS.staleBackoffMs() << staleStreak, FETCH_SETTINGS.baseIntervalMs());
        } else {
            long idealDelayMs = expectedNextFetchAtMs - nowMs;
            nextDelayMs = Math.max(idealDelayMs, FETCH_SETTINGS.staleBackoffMs());
        }

        Util.logMessage("Next fetch in %dms — expected=%d now=%d".formatted(nextDelayMs, expectedNextFetchAtMs, nowMs));

        scheduleFetch(nextDelayMs);
    }
}
