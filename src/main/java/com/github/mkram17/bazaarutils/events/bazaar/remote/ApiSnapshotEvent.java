package com.github.mkram17.bazaarutils.events.bazaar.remote;

import com.github.mkram17.bazaarutils.data.bazaar.book.BookLevels;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.Map;

/**
 * Carries a full Bazaar book snapshot from one Hypixel API poll.
 *
 * <p>Posted on the game thread by
 * {@link com.github.mkram17.bazaarutils.data.bazaar.book.remote.BazaarApiFetcher} immediately
 * after conversion. All subscribers run on the game thread and require no further marshalling.
 *
 * <p>{@code snapshot} maps each product ID to its paired bid and ask levels as of
 * {@code timestamp} ({@code lastUpdated} from the API reply). Identical consecutive snapshots
 * are suppressed at the fetcher — if this event fires, at least one product changed.
 */
public class ApiSnapshotEvent extends SkyBlockEvent {
    @Getter
    private final Map<String, BookLevels> snapshot;

    @Getter
    private final long timestamp;

    public ApiSnapshotEvent(Map<String, BookLevels> snapshot, long snapshotTs) {
        this.snapshot = snapshot;
        this.timestamp = snapshotTs;
    }
}