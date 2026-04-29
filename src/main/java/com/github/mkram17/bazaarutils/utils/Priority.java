package com.github.mkram17.bazaarutils.utils;

/**
 * Canonical priority constants for any ordered event or computation pipeline.
 *
 * <p>Semantics: <em>lower numeric value = runs earlier</em>. This convention matches
 * {@code @Subscription} from SkyBlockAPI and applies uniformly across all pipeline
 * participants — event subscribers, item modifiers, render hooks, etc.
 *
 * <p>The range intentionally spans {@link Integer#MIN_VALUE} to {@link Integer#MAX_VALUE}
 * so that any pipeline participant can anchor itself absolutely before or after all
 * others, without coordinating magic numbers. Named tiers cover the common cases;
 * raw integers are for fine-grained relative ordering only.
 */
public final class Priority {
    private Priority() {}

    /** Runs before anything else in the pipeline. Use for bootstrap/safety guards. */
    public static final int FIRST = Integer.MIN_VALUE;

    /** Before all standard tiers; for cross-cutting concerns (e.g. cancellation guards). */
    public static final int HIGHEST = -2_000_000;

    /** Before normal; for pre-processing or enrichment. */
    public static final int HIGH = -100_000;

    /** Default. Use when ordering relative to other handlers does not matter. */
    public static final int NORMAL = 0;

    /** After normal; for post-processing or aggregation. */
    public static final int LOW = 100_000;

    /** After all standard tiers; for cleanup or final decoration. */
    public static final int LOWEST = 2_000_000;

    /** Runs after everything else. Use for terminal/audit handlers. */
    public static final int LAST = Integer.MAX_VALUE;
}