package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class BazaarLimitsStorage {
    private static final Type TYPE = new TypeToken<List<BazaarLimitsVisualizer.OrderLimitEntry>>(){}.getType();

    public static final DataStorage<List<BazaarLimitsVisualizer.OrderLimitEntry>> INSTANCE = new DataStorage<>(ArrayList::new, "bazaar_limits", TYPE);

    private BazaarLimitsStorage() {}
}