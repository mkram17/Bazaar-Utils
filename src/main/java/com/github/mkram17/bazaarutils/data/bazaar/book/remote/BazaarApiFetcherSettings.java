package com.github.mkram17.bazaarutils.data.bazaar.book.remote;

/** Tuning parameters for {@link BazaarApiFetcher}'s polling cadence, backoff, and error thresholds. */
public record BazaarApiFetcherSettings(
        long baseIntervalMs,
        long postOffsetMs,
        long staleBackoffMs,
        long failureRetryMs,
        long failureErrorThreshold,
        int staleWarningThreshold
) {
    public static final BazaarApiFetcherSettings DEFAULT = new BazaarApiFetcherSettings(
            20_000L,
            500L,
            750L,
            500L,
            4L,
            5
    );
}
