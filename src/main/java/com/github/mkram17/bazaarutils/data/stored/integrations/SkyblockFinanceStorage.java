package com.github.mkram17.bazaarutils.data.stored.integrations;

import com.github.mkram17.bazaarutils.data.bazaar.activity.*;
import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.utils.annotations.modules.BazaarIntegration;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Persists the set of already-exported trade records for the skyblock.finance integration.
 *
 * <p>Stores a {@code Map<UUID, Integer>} of record id → exported amount. Amounts are
 * additive across exports: if 100 units were exported last session and 50 more have
 * been claimed since, the next export covers only the 50 new units.
 *
 * <p>On retention prune, any record ids that were dropped from activity storage are
 * also removed from this map — stale ids waste space and can theoretically collide
 * if UUIDs are reused (they won't be, but the cleanup is correct regardless).
 */
@BazaarIntegration(id = "skyblock_finance")
public final class SkyblockFinanceStorage implements BazaarActivityIntegration.StoragePrunable {
    /**
     * Maps order/deal UUID → how many units have already been exported.
     * Allows incremental export as an order accumulates more held claims over time.
     */
    public static final ProfileStorage<Map<UUID, Integer>> INSTANCE = new ProfileStorage<>(
            0,
            HashMap::new,
            "skyblock_finance_trade_exports",
            v -> Codec.unboundedMap(
                    Codec.STRING.xmap(UUID::fromString, UUID::toString),
                    Codec.INT
            ).xmap(HashMap::new, it -> it)
    );

    @Override
    public void onActivityPruned(List<BazaarActivityRecord> pruned) {
        var ids = pruned.stream().map(BazaarActivityRecord::id).collect(Collectors.toSet());

        INSTANCE.edit(map -> ids.forEach(map::remove));
    }
}