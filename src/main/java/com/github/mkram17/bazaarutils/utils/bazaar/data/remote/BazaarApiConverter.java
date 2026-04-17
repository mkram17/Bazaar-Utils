package com.github.mkram17.bazaarutils.utils.bazaar.data.remote;

import com.github.mkram17.bazaarutils.events.bazaar.BazaarApiSnapshotEvent;
import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BazaarApiConverter {

    public static BazaarApiSnapshotEvent convert(SkyBlockBazaarReply reply) {
        long timestamp = ((AccessorSkyBlockBazaarReply) reply).getLastUpdated();
        DataSources source = new DataSources.ApiSnapshot(timestamp);

        Map<String, Map.Entry<List<PriceLevel>, List<PriceLevel>>> batch = transformProducts(reply.getProducts(), timestamp, source);

        return new BazaarApiSnapshotEvent(batch, timestamp);
    }

    private static Map<String, Map.Entry<List<PriceLevel>, List<PriceLevel>>> transformProducts(Map<String, SkyBlockBazaarReply.Product> products, long timestamp, DataSources source) {
        if (products == null || products.isEmpty()) return Map.of();

        return products.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            SkyBlockBazaarReply.Product data = entry.getValue();

                            return Map.entry(
                                    toPriceLevels(data.getBuySummary(), timestamp, source),
                                    toPriceLevels(data.getSellSummary(), timestamp, source)
                            );
                        }
                ));
    }

    private static List<PriceLevel> toPriceLevels(List<SkyBlockBazaarReply.Product.Summary> summaries, long timestamp, DataSources source) {
        if (summaries == null || summaries.isEmpty()) return List.of();

        return summaries.stream()
                .map(summary -> new PriceLevel(summary.getPricePerUnit(), summary.getAmount(), (int) summary.getOrders(), timestamp, source))
                .toList();
    }
}