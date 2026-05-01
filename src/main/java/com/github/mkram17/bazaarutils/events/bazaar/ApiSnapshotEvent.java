package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.data.bazaar.book.BookLevels;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.Map;

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