package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import org.jetbrains.annotations.NotNull;

/**
 * An event fired for a specific {@link ProfileKey} — not necessarily whichever profile is
 * currently active. Exists so a handler that only cares about the active profile can say so
 * once ({@code @OnlyCurrentProfile}) instead of every such handler repeating
 * {@code event.getProfileKey().isCurrent()} as its own first line.
 */
public interface ProfileScopedEvent {
    @NotNull ProfileKey getProfileKey();
}