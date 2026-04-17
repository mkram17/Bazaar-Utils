package com.github.mkram17.bazaarutils.events.bazaar;

import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import lombok.Getter;
import tech.thatgravyboat.skyblockapi.api.events.base.SkyBlockEvent;

import java.util.List;
import java.util.Map;

public class BazaarApiSnapshotEvent extends SkyBlockEvent {
    @Getter
    public final Map<String, Map.Entry<List<PriceLevel>, List<PriceLevel>>> snapshot;

    @Getter
    public final long timestamp;

    public BazaarApiSnapshotEvent(Map<String, Map.Entry<List<PriceLevel>, List<PriceLevel>>> snapshot, long snapshotTs) {
        this.snapshot = snapshot;
        this.timestamp = snapshotTs;
    }
}