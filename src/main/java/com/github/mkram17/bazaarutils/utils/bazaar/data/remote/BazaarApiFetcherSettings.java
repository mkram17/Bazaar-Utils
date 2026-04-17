package com.github.mkram17.bazaarutils.utils.bazaar.data.remote;

public class BazaarApiFetcherSettings {
    public final long BASE_INTERVAL_MS = 20_000;
    public final long POST_OFFSET_MS = 500;
    public final long STALE_BACKOFF_MS = 750;
    public final long FAILURE_RETRY_MS = 500;
    public final int STALE_WARNING_THRESHOLD = 5;
}
