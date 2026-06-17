package com.github.mkram17.bazaarutils.features.notification;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.notification.NotificationsConfig;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class NotificationBus {

    private static final BazaarLogger LOG = BazaarLogger.of(NotificationBus.class);

    public static final float DEFAULT_VOLUME = 0.2f;
    public static final SoundEvent DEFAULT_SOUND = SoundEvents.UI_BUTTON_CLICK.value();

    /**
     * <pre>{@code
     * new NotificationBus.ChannelDispatcher<MySettings>(s -> s.channels)
     *     .batchWhen(() -> MyConfig.BATCH_TOGGLE)
     *     .on(CHAT,  (s, p) -> PlayerLogger.sendWithCommand(p.content().chatComponent(), s.clickCommand.commandFor(p.subject().label())))
     *     .on(SOUND, (s, p) -> PlayerLogger.playSound(DEFAULT_SOUND, DEFAULT_VOLUME))
     * }</pre>
     */
    public static final class ChannelDispatcher<S> {

        private final Function<S, NotificationChannelType[]> channelsOf;
        private final Map<NotificationChannelType, BiConsumer<S, NotificationPayload<S>>> strategies = new EnumMap<>(NotificationChannelType.class);
        private BooleanSupplier batchEnabled = () -> true;

        public ChannelDispatcher(Function<S, NotificationChannelType[]> channelsOf) {
            this.channelsOf = channelsOf;
        }

        public ChannelDispatcher<S> on(NotificationChannelType channel, BiConsumer<S, NotificationPayload<S>> strategy) {
            strategies.put(channel, strategy);

            return this;
        }

        public ChannelDispatcher<S> batchWhen(BooleanSupplier policy) {
            this.batchEnabled = policy;

            return this;
        }

        boolean isBatchEnabled() {
            return batchEnabled.getAsBoolean();
        }

        void dispatch(NotificationPayload<S> payload) {
            var channels = channelsOf.apply(payload.settings());
            if (channels == null || channels.length == 0) return;

            for (var channel : channels) {
                var strategy = strategies.get(channel);

                if (strategy != null) {
                    try {
                        strategy.accept(payload.settings(), payload);
                    } catch (Exception e) {
                        LOG.warn("[BUS] dispatch() — channel={} threw: {}", channel, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Produces a batched {@link NotificationPayload.Content} for {@code distinctCount}
     * distinct entities coalesced within the window. Only invoked when more than one
     * distinct {@link NotificationPayload.NotificationSubject#instanceKey()} contributed — see {@link #flush}.
     */
    @FunctionalInterface
    public interface BatchFormatter {
        NotificationPayload.Content format(int distinctCount, NotificationPayload<?> latest);
    }

    private record BatchKey(NotificationKind kind, String groupKey, Object settings) {}

    /**
     * Tracks one pending coalescing window. {@code representative} always holds the
     * most recently received payload — important for kinds like CLAIMED, where a later
     * event carries a more complete cumulative state than an earlier one.
     *
     * <p>{@code instanceKeys} counts distinct underlying entities (e.g. distinct order
     * UUIDs), not raw event count — see {@link #distinctCount()}.
     */
    private static final class Accumulator {
        int generation = 0;
        NotificationPayload<?> representative;
        private final Set<String> instanceKeys = new HashSet<>();

        Accumulator(NotificationPayload<?> p) {
            representative = p;
            instanceKeys.add(p.subject().instanceKey());
        }

        void absorb(NotificationPayload<?> p) {
            representative = p;
            instanceKeys.add(p.subject().instanceKey());
        }

        int distinctCount() {
            return instanceKeys.size();
        }
    }

    private static final Map<Class<?>, ChannelDispatcher<?>> DISPATCHERS = new HashMap<>();
    private static final Map<NotificationKind, BatchFormatter> BATCH_FORMATTERS = new HashMap<>();
    private static final Map<BatchKey, Accumulator> PENDING = new HashMap<>();

    private NotificationBus() {}

    public static <S> void registerDispatcher(Class<S> settingsClass, ChannelDispatcher<S> dispatcher) {
        DISPATCHERS.put(settingsClass, dispatcher);
    }

    public static void registerBatchFormatter(NotificationKind kind, BatchFormatter formatter) {
        BATCH_FORMATTERS.put(kind, formatter);
    }

    public static void osNotify(String text) {
        CompletableFuture.runAsync(() -> {
            try {
                TinyFileDialogs.tinyfd_notifyPopup(BazaarUtils.MOD_NAME, text, "info");
            } catch (Throwable t) {
                LOG.warn("OS notification failed: {}", t.getMessage());
            }
        });
    }

    public static void order(OrderNotificationKind kind, NotificationPayload.NotificationSubject subject, NotificationPayload.Content content) {
        var notifications = NotificationsConfig.forOrderNotificationKind(kind);
        if (notifications.isEmpty()) return;

        int windowTicks = NotificationsConfig.DEDUPLICATION_WINDOW_TICKS;

        for (var setting : notifications) {
            var payload = new NotificationPayload<>(kind, subject, content, setting);

            if (windowTicks == 0) {
                dispatch(payload);
            } else {
                accumulate(payload, windowTicks);
            }
        }
    }

    private static <S> void accumulate(NotificationPayload<S> payload, int windowTicks) {
        var key = new BatchKey(payload.kind(), payload.subject().groupKey(), payload.settings());

        var existing = PENDING.get(key);

        if (existing == null) {
            var acc = new Accumulator(payload);
            PENDING.put(key, acc);

            scheduleFlush(key, acc.generation, windowTicks);
        } else {
            existing.absorb(payload);
            existing.generation++;
            scheduleFlush(key, existing.generation, windowTicks);
        }
    }

    @SuppressWarnings("unchecked")
    private static <S> void flush(BatchKey key, int expectedGeneration) {
        var acc = PENDING.get(key);
        if (acc == null || acc.generation != expectedGeneration) return;

        PENDING.remove(key);

        var payload = (NotificationPayload<S>) acc.representative;
        var runtimeClass = payload.settings().getClass();

        var dispatcher = (ChannelDispatcher<S>) DISPATCHERS.get(runtimeClass);
        if (dispatcher == null) return;

        int distinct = acc.distinctCount();

        // distinct > 1: genuinely different entities fired — ask the formatter for a summary.
        // distinct == 1: the SAME entity fired repeatedly (e.g. trickling partial claims) —
        // dispatch its latest state as-is. Collapses the chat spam to one message without
        // asserting an order count the data never supported.
        if (distinct > 1 && dispatcher.isBatchEnabled()) {
            var formatter = BATCH_FORMATTERS.get(payload.kind());

            if (formatter != null) {
                payload = new NotificationPayload<>(
                        payload.kind(),
                        payload.subject(),
                        formatter.format(distinct, payload),
                        payload.settings()
                );
            }
        }

        dispatcher.dispatch(payload);
    }

    private static void scheduleFlush(BatchKey key, int generation, int windowTicks) {
        long delayMs = windowTicks * 50L;

        CompletableFuture
                .delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> Minecraft.getInstance().execute(() -> flush(key, generation)));
    }

    @SuppressWarnings("unchecked")
    private static <S> void dispatch(NotificationPayload<S> payload) {
        var runtimeClass = payload.settings().getClass();
        var dispatcher = (ChannelDispatcher<S>) DISPATCHERS.get(runtimeClass);

        if (dispatcher == null) return;

        dispatcher.dispatch(payload);
    }
}