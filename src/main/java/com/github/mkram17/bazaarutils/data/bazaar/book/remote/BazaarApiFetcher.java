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
 * whenever a new snapshot is received.
 *
 * <p>The next fetch is scheduled relative to the snapshot's own {@code lastUpdated} timestamp
 * ({@code lastUpdated + BASE_INTERVAL_MS + POST_OFFSET_MS}) rather than wall-clock time, so
 * fetches stay aligned with Hypixel's actual update cadence rather than drifting.
 *
 * <p>At most one fetch task is outstanding at a time; {@code SCHED_LOCK} serializes
 * schedule/cancel. Consecutive failures back off to {@code FAILURE_RETRY_MS} and surface an
 * error to the player after {@code FAILURE_ERROR_THRESHOLD} consecutive failures.
 * Consecutive identical snapshots are tracked; a warning fires at {@code STALE_WARNING_THRESHOLD}.
 */
public final class BazaarApiFetcher {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarApiFetcher.class);

    private static final BazaarApiFetcherSettings FETCH_SETTINGS = new BazaarApiFetcherSettings();

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
        LOG.info("BazaarApiFetcher initialized (simple fixed-interval poller) — base interval {}ms, post-offset {}ms", FETCH_SETTINGS.BASE_INTERVAL_MS, FETCH_SETTINGS.POST_OFFSET_MS);
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
            LOG.error("Unexpected throwable escaped fetch loop — scheduling retry", throwable);
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
                long snapshotTs = event.getTimestamp(); // or however your event exposes it

                if (snapshotTs <= 0) {
                    handleFetchFailure("lastUpdated <= 0", new RuntimeException("lastUpdated <= 0"), false);

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

    private static void handleFetchFailure(String reason, Throwable cause, boolean countFailures) {
        int failureCount = countFailures ? consecutiveFailures.incrementAndGet() : consecutiveFailures.get();

        LOG.warn("Fetch failure: {} — retry in {}ms (consecutive={})", reason, FETCH_SETTINGS.FAILURE_RETRY_MS, failureCount, cause);

        if (countFailures && failureCount >= FETCH_SETTINGS.FAILURE_ERROR_THRESHOLD) {
            PlayerLogger.sendError("API fetch has failed " + failureCount + " times in a row — price data is stale.", cause);
        }

        scheduleFailureRetry();
    }

    private static void scheduleFailureRetry() {
        scheduleFetch(FETCH_SETTINGS.FAILURE_RETRY_MS);
    }

    private static void handleSnapshotResult(ApiSnapshotEvent event, long snapshotTs) {
        if (snapshotTs != lastSnapshotTs) {
            handleNewSnapshot(event, snapshotTs);

            return;
        }

        handleUnchangedSnapshot(snapshotTs);
    }

    private static void handleNewSnapshot(ApiSnapshotEvent event, long snapshotTs) {
        long previousSnapshotTs = lastSnapshotTs;

        lastSnapshotTs = snapshotTs;
        consecutiveIdenticalSnapshots.set(0);

        // Marshall to game thread before posting — the event bus has no thread
        // safety guarantees and all subscribers expect to run on the game thread.
        Minecraft.getInstance().execute(() -> event.post(EVENT_BUS));

        if (previousSnapshotTs == -1) {
            LOG.info("First API snapshot received — ts={}", snapshotTs);

            return;
        }

        PlayerLogger.debug("API snapshot received — ts=" + snapshotTs + " (Δ" + (snapshotTs - previousSnapshotTs) + "ms)", NotificationType.API, LOG);
    }

    private static void handleUnchangedSnapshot(long snapshotTs) {
        int count = consecutiveIdenticalSnapshots.incrementAndGet();

        LOG.debug("Snapshot unchanged — ts={} x{}", snapshotTs, count);

        if (count == FETCH_SETTINGS.STALE_WARNING_THRESHOLD) {
            LOG.warn("API stale snapshot x{} — ts={} — server may be lagging or BASE_INTERVAL_MS too short", count, snapshotTs);
        }
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long nowMs = System.currentTimeMillis();
        long expectedNextFetchAtMs = snapshotTs + FETCH_SETTINGS.BASE_INTERVAL_MS + FETCH_SETTINGS.POST_OFFSET_MS;

        long nextDelayMs;
        if (nowMs >= expectedNextFetchAtMs) {
            nextDelayMs = FETCH_SETTINGS.STALE_BACKOFF_MS;
        } else {
            long idealDelayMs = expectedNextFetchAtMs - nowMs;
            nextDelayMs = Math.max(idealDelayMs, FETCH_SETTINGS.STALE_BACKOFF_MS);
        }

        LOG.debug("Next fetch in {}ms — expected={} now={}", nextDelayMs, expectedNextFetchAtMs, nowMs);

        scheduleFetch(nextDelayMs);
    }
}