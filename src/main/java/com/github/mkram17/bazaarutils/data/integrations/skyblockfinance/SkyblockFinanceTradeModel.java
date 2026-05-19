package com.github.mkram17.bazaarutils.data.integrations.skyblockfinance;

import com.github.mkram17.bazaarutils.data.bazaar.activity.*;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityRecord.*;
import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.skyblockfinance.activity.SkyblockFinanceActivityExporter;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

/**
 * A cost-basis position record for skyblock.finance's Trades feature.
 *
 * <p>Represents items the player acquired and holds, valued at the price paid.
 * Three acquisition paths are modelled:
 * <ol>
 *   <li>Instant buy — {@code openedAt == closedAt == executedAt}. Position is
 *       immediately closed since the items are in hand.</li>
 *   <li>Claimed buy order — {@code openedAt} is {@code placedAt};
 *       {@code closedAt} is {@link OrderStatus.Claimed#claimedAt()} when the order
 *       is terminal, or {@code null} while still open (partial claims possible).</li>
 *   <li>Cancelled flip sell — items returned at the <em>source buy's</em> cost basis,
 *       not the intended sell price. {@code openedAt} is the flip sell's {@code placedAt};
 *       {@code closedAt} is {@link OrderStatus.Cancelled#cancelledAt()}.</li>
 * </ol>
 *
 * <h2>closedAt nullability</h2>
 * {@code null} means the position is open — the player holds the items but the
 * buy order is still live and may accumulate more claimed volume. skyblock.finance
 * should treat a null {@code closedAt} as an open/active position.
 *
 * <h2>Export deduplication</h2>
 * {@link #subtractExported(int)} produces a copy with the already-exported amount
 * subtracted. This supports incremental export — a partially-claimed order can be
 * exported multiple times as more volume is claimed, with each export covering only
 * the new volume since the last export.
 */
public record SkyblockFinanceTradeModel(
        UUID sourceId,
        String productId,
        double pricePerUnit,
        int amount,
        long openedAt,
        @Nullable Long closedAt
) implements BazaarActivityIntegration.ActivityExportable.ExportEntry {
    public static final Codec<SkyblockFinanceTradeModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("sourceId").forGetter(SkyblockFinanceTradeModel::sourceId),
            Codec.STRING.fieldOf("item").forGetter(SkyblockFinanceTradeModel::productId),
            Codec.DOUBLE.fieldOf("pricePerUnit").forGetter(SkyblockFinanceTradeModel::pricePerUnit),
            Codec.INT.fieldOf("amount").forGetter(SkyblockFinanceTradeModel::amount),
            Codec.LONG.fieldOf("openedAt").forGetter(SkyblockFinanceTradeModel::openedAt),
            Codec.LONG.optionalFieldOf("closedAt", null).forGetter(SkyblockFinanceTradeModel::closedAt)
    ).apply(instance, SkyblockFinanceTradeModel::new));

    public SkyblockFinanceTradeModel subtractExported(int alreadyExported) {
        return new SkyblockFinanceTradeModel(sourceId, productId, pricePerUnit, amount - alreadyExported, openedAt, closedAt);
    }

    /**
     * Projects all held BUY activity into trade records at full held amount.
     * Export deduplication is applied downstream in
     * {@link SkyblockFinanceActivityExporter#pending()}.
     */
    public static final BazaarActivityFold<List<SkyblockFinanceTradeModel>> HELD_TRADES =
            BazaarActivityFold.filtering(
                    SkyblockFinanceTradeModel::isHeldPosition,
                    BazaarActivityFold.collecting(Collectors.flatMapping(
                            SkyblockFinanceTradeModel::fromActivity,
                            Collectors.toList()
                    ))
            );

    private static Stream<SkyblockFinanceTradeModel> fromActivity(BazaarActivityRecord record) {
        return switch (record) {
            // Held position — immediate acquisition
            case InstantBuy buy ->
                    Stream.of(new SkyblockFinanceTradeModel(
                            buy.id(), buy.productId(), buy.pricePerUnit(),
                            buy.volume(), buy.executedAt(), buy.executedAt()));

            // Held position — items claimed from a buy order, flip volume excluded
            case BuyOrderActivity buy when buy.playerClaimedAmount() > 0 ->
                    Stream.of(new SkyblockFinanceTradeModel(
                            buy.id(), buy.productId(), buy.pricePerItem(),
                            buy.playerClaimedAmount(), buy.placedAt(),
                            switch (buy.status()) {
                                case OrderStatus.Claimed status -> status.claimedAt();
                                default -> null;
                            }
                    ));

            // Cancelled flip sell — items returned to player, either to their inventory or to stash.
            // Cost basis is the original buy order's price, not the intended sell price.
            case FlipSellActivity flip
                    when flip.status() instanceof OrderStatus.Cancelled
                    && flip.returnedAmount() > 0 ->
                    Stream.of(new SkyblockFinanceTradeModel(
                            flip.id(), flip.productId(),
                            flip.sourcePricePerItem(),
                            flip.returnedAmount(),
                            flip.placedAt(),
                            ((OrderStatus.Cancelled) flip.status()).cancelledAt()));
            default -> Stream.empty();
        };
    }

    private static boolean isHeldPosition(BazaarActivityRecord record) {
        return record instanceof InstantBuy
                || (record instanceof BuyOrderActivity buy && buy.playerClaimedAmount() > 0)
                || (record instanceof FlipSellActivity flip
                && flip.status() instanceof OrderStatus.Cancelled
                && flip.returnedAmount() > 0);
    }
}