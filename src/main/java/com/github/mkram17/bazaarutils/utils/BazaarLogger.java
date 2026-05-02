package com.github.mkram17.bazaarutils.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Per-class logger wrapper. Each class that needs logging should declare:
 *
 * <pre>{@code
 *   private static final BazaarLogger LOG = BazaarLogger.of(MyClass.class);
 * }</pre>
 *
 * For errors that should also notify the player in chat, use {@link PlayerLogger#sendError} instead.
 *
 * <p><b>Level contract:</b>
 * <ul>
 *   <li>{@code info/warn/error} — always visible in {@code latest.log}; use for events
 *       that matter for user bug reports.</li>
 *   <li>{@code debug} — only visible in {@code logs/debug.log}, or when
 *       {@code -Dbazaarutils.debug=true} is set (promotes to INFO).</li>
 * </ul>
 */
public final class BazaarLogger {

    /**
     * When true, debug calls are promoted to INFO so they appear in latest.log.
     * Enable with -Dbazaarutils.debug=true in JVM args.
     */
    public static final boolean LOG_ALL = Boolean.getBoolean("bazaarutils.debug");

    private static final Marker ROOT_MARKER = MarkerManager.getMarker("BazaarUtils");

    private final Logger logger;
    private final Marker marker;

    private BazaarLogger(Logger logger, Marker marker) {
        this.logger = logger;
        this.marker = marker;
    }

    /**
     * Creates a logger bound to the given class.
     * Log output will read: [HH:mm:ss] [thread/LEVEL] (com.your.ClassName) [BazaarUtils] message
     */
    public static BazaarLogger of(Class<?> clazz) {
        return new BazaarLogger(LogManager.getLogger(clazz), ROOT_MARKER);
    }

    public boolean isDebugEnabled() {
        return LOG_ALL || logger.isDebugEnabled();
    }

    public void info(String message, Object... args) {
        logger.info(marker, message, args);
    }

    public void warn(String message, Object... args) {
        logger.warn(marker, message, args);
    }

    public void error(String message, Object... args) {
        logger.error(marker, message, args);
    }

    /**
     * Writes to debug. When -Dbazaarutils.debug=true is set,
     * promoted to INFO.
     */
    public void debug(String message, Object... args) {
        if (LOG_ALL) {
            logger.info(marker, "[DEBUG] " + message, args);
        } else {
            logger.debug(marker, message, args);
        }
    }
}