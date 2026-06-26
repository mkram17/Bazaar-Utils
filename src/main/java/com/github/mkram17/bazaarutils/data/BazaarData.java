package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.misc.orderinfo.PriceInfoContainer;
import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.Util;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public final class BazaarData {

    private static final long BASE_INTERVAL_MS = 20_000;
    private static final long POST_OFFSET_MS = 500;
    private static final long STALE_BACKOFF_MS = 750;
    private static final long FAILURE_RETRY_MS = 500;
    private static final int STALE_WARNING_THRESHOLD = 5;

    @Getter
    private static volatile SkyBlockBazaarReply currentReply;
    @Getter
    private static volatile long lastSnapshotTs = -1;
    private static volatile long lastFetchWallClock = -1;

    private static volatile ScheduledFuture<?> scheduledTask;
    private static final Object SCHED_LOCK = new Object();

    private static final AtomicInteger consecutiveIdenticalSnapshots = new AtomicInteger(0);
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /* Cached conversions: lowercase name -> productId */
    private static volatile Map<String, String> nameToProductIdCache = Map.of();
    @Setter
    private static volatile boolean conversionsLoaded = false;

    private BazaarData() {}

    @RunOnInit
    public static void init() {
        scheduleFetch(0);
        PlayerActionUtil.notifyAll("BazaarData initialized (simple fixed-interval poller). Base=" + BASE_INTERVAL_MS + "ms", Util.notificationTypes.BAZAARDATA);
    }


    private static void scheduleFetch(long delayMs) {
        synchronized (SCHED_LOCK) {
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false);
            }
            scheduledTask = BazaarUtils.BUExecutorService.schedule(BazaarData::fetchOnceSafely, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void fetchOnceSafely() {
        try {
            fetchOnce();
        } catch (Throwable t) {
            Util.notifyError("Unexpected error in BazaarData fetch loop", t);
            scheduleFetch(FAILURE_RETRY_MS);
        }
    }

    private static void fetchOnce() {
        lastFetchWallClock = System.currentTimeMillis();
        APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
            if (throwable != null) {
                consecutiveFailures.incrementAndGet();
                PlayerActionUtil.notifyAll("Fetch failure (" + throwable.getClass().getSimpleName() + "). Retry in " + FAILURE_RETRY_MS + "ms (failures=" + consecutiveFailures.get() + ")", Util.notificationTypes.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }
            if (reply == null || !reply.isSuccess()) {
                consecutiveFailures.incrementAndGet();
                PlayerActionUtil.notifyAll("Null/unsuccessful reply. Retry in " + FAILURE_RETRY_MS + "ms (failures=" + consecutiveFailures.get() + ")", Util.notificationTypes.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }
            consecutiveFailures.set(0);

            long snapshotTs = extractLastUpdated(reply);
            if (snapshotTs <= 0) {
                PlayerActionUtil.notifyAll("Invalid lastUpdated <= 0. Retry in " + FAILURE_RETRY_MS + "ms", Util.notificationTypes.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }

            if (snapshotTs != lastSnapshotTs) {
                long previous = lastSnapshotTs;
                lastSnapshotTs = snapshotTs;
                currentReply = reply;
                consecutiveIdenticalSnapshots.set(0);

                EVENT_BUS.post(new BazaarDataUpdateEvent(reply));

                if (previous != -1) {
                    PlayerActionUtil.notifyAll("New snapshot " + snapshotTs + " (Δ " + (snapshotTs - previous) + " ms). Scheduling next predicted fetch.", Util.notificationTypes.BAZAARDATA);
                } else {
                    PlayerActionUtil.notifyAll("First snapshot " + snapshotTs + " received.", Util.notificationTypes.BAZAARDATA);
                }

                scheduleNextFromSnapshot(snapshotTs);
            } else {
                int identical = consecutiveIdenticalSnapshots.incrementAndGet();
                PlayerActionUtil.notifyAll("Snapshot unchanged (" + snapshotTs + ") x" + identical, Util.notificationTypes.BAZAARDATA);
                if (identical == STALE_WARNING_THRESHOLD) {
                    PlayerActionUtil.notifyAll("WARNING: " + identical + " identical snapshots in a row. Server might be lagging or BASE_INTERVAL_MS too short.", Util.notificationTypes.BAZAARDATA);
                }
                scheduleNextFromSnapshot(snapshotTs);
            }
        });
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long now = System.currentTimeMillis();
        long target = snapshotTs + BASE_INTERVAL_MS + POST_OFFSET_MS;

        long delay;
        if (now >= target) {
            // Past the ideal fetch time; server hasn’t advanced snapshot yet. Don’t spam: back off.
            delay = STALE_BACKOFF_MS;
        } else {
            var typicalDelay = target - now;
            delay = Math.max(typicalDelay, STALE_BACKOFF_MS);
        }
        scheduleFetch(delay);
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
     * Get the number of orders at an exact price for a product & price type.
     * @return OptionalInt empty if reply / product / priceType invalid or not found.
     */
    public static OptionalInt getOrderCountOptional(String productId, PriceInfoContainer.PriceType priceType, double price) {
        SkyBlockBazaarReply reply = currentReply;
        if (reply == null || productId == null || priceType == null) return OptionalInt.empty();

        try {
            SkyBlockBazaarReply.Product product = reply.getProduct(productId);
            if (product == null) return OptionalInt.empty();

            List<SkyBlockBazaarReply.Product.Summary> list = switch (priceType) {
                case INSTABUY -> product.getBuySummary();
                case INSTASELL -> product.getSellSummary();
            };

            if (list == null) return OptionalInt.empty();
            for (SkyBlockBazaarReply.Product.Summary s : list) {
                if (Double.compare(s.getPricePerUnit(), price) == 0) {
                    return OptionalInt.of((int) s.getOrders());
                }
            }
            return OptionalInt.of(0);
        } catch (Exception e) {
            Util.notifyError("Error in getOrderCountOptional for productId=" + productId, e);
            return OptionalInt.empty();
        }
    }

    /**
     * Empty can mean: reply/product/priceType invalid or not found; exception while finding price
     * INSTABUY (top of buySummary aka people's sell orders). INSTASELL (top of sellSummary, aka people's buy orders).
     * @return OptionalDouble price found.
     */
    public static OptionalDouble findItemPriceOptional(String productId, PriceInfoContainer.PriceType priceType) {
        SkyBlockBazaarReply reply = currentReply;
        if (reply == null || productId == null || priceType == null)
            return OptionalDouble.empty(); //TODO maybe throw error here instead. Needs testing to make sure it doesn't happen too frequently or at times where it is expected behavior

        try {
            SkyBlockBazaarReply.Product product = reply.getProduct(productId);
            if (product == null)
                return OptionalDouble.empty();

            return switch (priceType) {
                case INSTABUY -> {
                    List<SkyBlockBazaarReply.Product.Summary> buySummary = product.getBuySummary();
                    if (buySummary == null || buySummary.isEmpty()) yield OptionalDouble.of(0.0);
                    yield OptionalDouble.of(buySummary.getFirst().getPricePerUnit());
                }
                case INSTASELL -> {
                    List<SkyBlockBazaarReply.Product.Summary> sellSummary = product.getSellSummary();
                    if (sellSummary == null || sellSummary.isEmpty()) yield OptionalDouble.of(0.0);
                    yield OptionalDouble.of(sellSummary.getFirst().getPricePerUnit());
                }
            };
        } catch (Exception e) {
            Util.notifyError("Error in findItemPriceOptional for productId=" + productId, e);
            return OptionalDouble.empty();
        }
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) return Optional.empty();
        ensureConversionsLoaded();
        String cleanName = net.minecraft.ChatFormatting.stripFormatting(naturalName);
        if (cleanName == null) cleanName = naturalName;
        cleanName = cleanName.trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(nameToProductIdCache.get(cleanName));
    }

    /**
     * Cached conversion load. Thread-safe (single pass).
     */
    private static void ensureConversionsLoaded() {
        if (conversionsLoaded) return;
        synchronized (BazaarData.class) {
            if (conversionsLoaded) return;
            try {
                Map<String, String> mutable = new HashMap<>();
                var resources = ResourceManager.getResourceJson();
                var conversions = resources.has("bazaarConversions") ? resources.getAsJsonObject("bazaarConversions") : resources;
                for (String key : conversions.keySet()) {
                    String value = conversions.get(key).getAsString();
                    if (value != null) {
                        mutable.put(value.toLowerCase(Locale.ROOT), key);
                    }
                }
                nameToProductIdCache = Collections.unmodifiableMap(mutable);
                conversionsLoaded = true;
                PlayerActionUtil.notifyAll("Loaded bazaarConversions cache: " + nameToProductIdCache.size() + " entries.", Util.notificationTypes.BAZAARDATA);
            } catch (Exception e) {
                Util.notifyError("Failed loading bazaarConversions cache", e);
                nameToProductIdCache = Map.of();
                conversionsLoaded = true;
            }
        }
    }

    @Deprecated
    public static Double findItemPrice(String productId, PriceInfoContainer.PriceType priceType) {
        return findItemPriceOptional(productId, priceType).orElse(-1.0);
    }

    @Deprecated
    public static String findProductId(String name) {
        return findProductIdOptional(name).orElse(null);
    }

    @Deprecated
    public static int getOrderCount(String productId, PriceInfoContainer.PriceType priceType, double price) {
        return getOrderCountOptional(productId, priceType, price).orElse(-1);
    }

    public static Optional<Duration> getCurrentSnapshotAge() {
        long ts = lastSnapshotTs;
        if (ts <= 0) return Optional.empty();
        return Optional.of(Duration.ofMillis(System.currentTimeMillis() - ts));
    }

    public static Optional<Duration> getTimeSinceLastFetchAttempt() {
        long f = lastFetchWallClock;
        if (f <= 0) return Optional.empty();
        return Optional.of(Duration.ofMillis(System.currentTimeMillis() - f));
    }
}
