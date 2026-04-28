package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

public final class BazaarLimitsStorage {
    public static final ProfileStorage<List<BazaarLimitsVisualizer.OrderLimitEntry>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "bazaar_limits",
            v -> Codec.list(BazaarLimitsVisualizer.OrderLimitEntry.CODEC).xmap(ArrayList::new, ArrayList::new)
    );

    private BazaarLimitsStorage() {}
}