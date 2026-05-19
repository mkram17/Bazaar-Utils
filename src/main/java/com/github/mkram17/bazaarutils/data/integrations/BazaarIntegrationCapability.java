package com.github.mkram17.bazaarutils.data.integrations;

/**
 * Root marker for all integration capability domains.
 *
 * <p>Sealed so the registry can reason exhaustively about what a registered instance
 * might implement. Each domain (activity tracking, future pricing, notifications, etc.)
 * gets its own sub-sealed interface as a direct permit.
 *
 * <p>{@link CapabilityManifest} is deliberately not a permit — it is a free interface
 * that any class implementing any capability may also implement to provide display
 * identity within their integrated routines. It is not a domain in its own right.
 */
public sealed interface BazaarIntegrationCapability permits BazaarActivityIntegration {
    /**
     * Optional identity contract for any integration capability.
     */
    interface CapabilityManifest {
        String displayName();

        String description();
    }
}