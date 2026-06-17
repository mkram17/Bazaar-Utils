package com.github.mkram17.bazaarutils.features.notification;

import net.minecraft.network.chat.MutableComponent;

/**
 * Immutable, domain-neutral data transfer object. Generic over the concrete settings
 * type {@code S} — the bus never inspects {@code S} directly; channel strategies
 * registered on {@link NotificationBus.ChannelDispatcher} extract what they need via lambdas.
 *
 * <p>{@link #subject()} sits alongside {@code kind} rather than inside {@link Content} —
 * it identifies and routes the notification (label for commands, keys for batching) and is
 * read by the bus itself ({@code NotificationBus.accumulate}); {@code Content} holds only
 * what channel strategies render, and never needs to know what it's about.
 */
public record NotificationPayload<S>(
        NotificationKind    kind,
        NotificationSubject subject,
        Content             content,
        S                   settings
) {
    /**
     * All pre-computed channel representations for one notification event. Purely
     * rendering output — carries no identity or routing information; see {@link NotificationSubject}.
     *
     * <ul>
     *   <li>{@link #chatComponent()}  — CHAT</li>
     *   <li>{@link #screenTitle()}    — SCREEN</li>
     *   <li>{@link #plainText()}      — OS (tinyfd has no MC formatting)</li>
     *   <li>{@link #discordPayload()} — REMOTE</li>
     * </ul>
     */
    public record Content(
            MutableComponent chatComponent,
            String           plainText,
            DiscordPayload   discordPayload,
            ScreenTitle      screenTitle
    ) {
        /**
         * Short two-line overlay for the {@link NotificationChannelType#SCREEN} channel.
         * Title: the kind label, concise and colored. Subtitle: the item name in gold.
         */
        public record ScreenTitle(MutableComponent title, MutableComponent subtitle) {}

        public static Content of(MutableComponent component, DiscordPayload.Embed embed, ScreenTitle screenTitle) {
            return new Content(component, component.getString(), DiscordPayload.embedOnly(embed), screenTitle);
        }

        /** Full control — caller supplies a pre-built Discord payload. */
        public static Content of(MutableComponent component, DiscordPayload discord, ScreenTitle screenTitle) {
            return new Content(component, component.getString(), discord, screenTitle);
        }
    }

    /**
     * Identifies what a notification is about, at three granularities.
     *
     * @param label       human-readable name used in messages and as the argument to
     *                     click/auto commands (e.g. an item's display name).
     * @param groupKey    coalescing key — events sharing {@code (kind, groupKey, settings)}
     *                     within the deduplication window are batched into one summary.
     *                     Coarse by design: e.g. {@code productId + ":" + side}. Never a
     *                     per-event or per-instance identifier.
     * @param instanceKey stable identity of the concrete entity that fired this event
     *                     (e.g. an order's UUID). Used only to tell "N distinct entities
     *                     fired" apart from "1 entity fired N times" inside a batch —
     *                     never used for grouping itself.
     */
    public record NotificationSubject(String label, String groupKey, String instanceKey) {}
}