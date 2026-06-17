package com.github.mkram17.bazaarutils.features.notification;

/**
 * Marker interface for all notification kind enums.
 * Sealed so the compiler enforces exhaustive switches at the concrete handler level,
 * and prevents the bus from coupling to any specific domain's enum.
 *
 * @see OrderNotificationKind
 */
public sealed interface NotificationKind permits OrderNotificationKind {}