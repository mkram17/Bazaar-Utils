package com.github.mkram17.bazaarutils.data.bazaar.book.remote;

import com.github.mkram17.bazaarutils.events.bazaar.remote.ApiSnapshotEvent;
import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.book.BookLevels;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BazaarApiConverter {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarApiConverter.class);

    public static ApiSnapshotEvent convert(SkyBlockBazaarReply reply) {
        long timestamp = ((AccessorSkyBlockBazaarReply) reply).getLastUpdated();
        BazaarDataOrigin origin = new BazaarDataOrigin.ApiSnapshot(timestamp);

        Map<String, SkyBlockBazaarReply.Product> products = reply.getProducts();

        LOG.debug("Converting API reply — ts={} products={}", timestamp, products != null ? products.size() : 0);

        Map<String, BookLevels> batch = transformProducts(products, origin);

        return new ApiSnapshotEvent(batch, timestamp);
    }

    private static Map<String, BookLevels> transformProducts(Map<String, SkyBlockBazaarReply.Product> products, BazaarDataOrigin origin) {
        if (products == null || products.isEmpty()) return Map.of();

        return products.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            SkyBlockBazaarReply.Product data = entry.getValue();

                            return new BookLevels(
                                    toPriceLevels(data.getBuySummary(), origin),
                                    toPriceLevels(data.getSellSummary(), origin)
                            );
                        }
                ));
    }

    private static List<PriceLevel> toPriceLevels(List<SkyBlockBazaarReply.Product.Summary> summaries, BazaarDataOrigin origin) {
        if (summaries == null || summaries.isEmpty()) return List.of();

        return summaries.stream()
                .map(summary -> new PriceLevel(summary.getPricePerUnit(), summary.getAmount(), (int) summary.getOrders(), origin))
                .toList();
    }
}