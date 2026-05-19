package com.github.mkram17.bazaarutils.data.bazaar.activity;

import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * A single unit of recorded Bazaar player activity, persisted for the duration of the
 * configured retention window.
 *
 * <h2>Shape breakdown</h2>
 * <ul>
 *   <li>{@link InstantBuy} / {@link InstantSell} — atomic, immutable. One record per
 *       event processed. No lifecycle nor logical reductions.</li>
 *   <li>{@link BuyOrderActivity} — one record per tracked buy order, mutated in-place
 *       as {@link UserOrderEvent}s arrive.
 *       Keyed by the same UUID as the underlying {@link Order}.</li>
 *   <li>{@link SellOfferActivity} — same lifecycle pattern as BuyOrderActivity, but for
 *       sell offers placed directly by the player (not via flip).</li>
 *   <li>{@link FlipSellActivity} — a sell offer synthesised by a flip. Structurally
 *       identical to SellOfferActivity except it carries {@link FlipSellActivity#sourceId},
 *       linking it back to the buy order whose fill funded it. This link is load-bearing:
 *       if this offer is later cancelled, cost-basis attribution for the returned items
 *       requires resolving the source buy's {@code pricePerItem}.</li>
 * </ul>
 *
 * <h2>Timestamps</h2>
 * Timestamps are not stored as nullable fields — they live inside the {@link OrderStatus}
 * variant. {@link OrderStatus.Filled#filledAt()}, {@link OrderStatus.Claimed#claimedAt()},
 * and {@link OrderStatus.Cancelled#cancelledAt()} are only accessible when the status
 * is that specific variant. Pattern-match on {@code status()} to extract them.
 *
 * <h2>Claimed volume semantics on BuyOrderActivity</h2>
 * {@code claimedAmount} counts ALL volume claimed against this order, including volume
 * claimed as part of a flip. {@link BuyOrderActivity#playerClaimedAmount()} subtracts
 * {@link BuyOrderActivity#flippedAmount} to give the volume the player physically holds.
 * Any projection that reports "what the player owns" must use {@code playerClaimedAmount()},
 * not {@code claimedAmount} directly.
 */
public sealed interface BazaarActivityRecord
        permits BazaarActivityRecord.InstantBuy,
        BazaarActivityRecord.InstantSell,
        BazaarActivityRecord.BuyOrderActivity,
        BazaarActivityRecord.SellOfferActivity,
        BazaarActivityRecord.FlipSellActivity {

    UUID id();
    String productId();
    long recordedAt();

    Codec<BazaarActivityRecord> CODEC = Codec.STRING.dispatch(
            "type",
            record -> switch (record) {
                case InstantBuy ignored -> "instant_buy";
                case InstantSell ignored -> "instant_sell";
                case BuyOrderActivity ignored -> "buy_order";
                case SellOfferActivity ignored -> "sell_offer";
                case FlipSellActivity ignored -> "flip";
            },
            type -> switch (type) {
                case "instant_buy" -> InstantBuy.CODEC;
                case "instant_sell" -> InstantSell.CODEC;
                case "buy_order" -> BuyOrderActivity.CODEC;
                case "sell_offer" -> SellOfferActivity.CODEC;
                case "flip" -> FlipSellActivity.CODEC;
                default -> throw new IllegalArgumentException("Unknown activity record type: " + type);
            }
    );

    record InstantBuy(
            UUID id,
            String productId,
            double pricePerUnit,
            int volume,
            long executedAt
    ) implements BazaarActivityRecord {
        static final MapCodec<InstantBuy> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(InstantBuy::id),
                Codec.STRING.fieldOf("productId").forGetter(InstantBuy::productId),
                Codec.DOUBLE.fieldOf("pricePerUnit").forGetter(InstantBuy::pricePerUnit),
                Codec.INT.fieldOf("volume").forGetter(InstantBuy::volume),
                Codec.LONG.fieldOf("executedAt").forGetter(InstantBuy::executedAt)
        ).apply(i, InstantBuy::new));

        @Override
        public long recordedAt() {
            return executedAt;
        }
    }

    record InstantSell(
            UUID id,
            String productId,
            double pricePerUnit,
            int volume,
            long executedAt
    ) implements BazaarActivityRecord {
        static final MapCodec<InstantSell> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(InstantSell::id),
                Codec.STRING.fieldOf("productId").forGetter(InstantSell::productId),
                Codec.DOUBLE.fieldOf("pricePerUnit").forGetter(InstantSell::pricePerUnit),
                Codec.INT.fieldOf("volume").forGetter(InstantSell::volume),
                Codec.LONG.fieldOf("executedAt").forGetter(InstantSell::executedAt)
        ).apply(i, InstantSell::new));

        @Override
        public long recordedAt() {
            return executedAt;
        }
    }

    /**
     * A tracked buy order, updated in-place across its lifecycle.
     *
     * <h2>flippedAmount vs playerClaimedAmount</h2>
     * When a filled buy order is flipped, Hypixel claims the remaining filled volume
     * on the player's behalf and immediately lists it as a sell offer. That volume
     * never passes through the player's inventory — it goes straight back to the market.
     * {@code flippedAmount} tracks how much was claimed/consumed this way.
     * {@link #playerClaimedAmount()} = {@code claimedAmount - flippedAmount} is the
     * volume the player actually retrieved and holds.
     */
    record BuyOrderActivity(
            UUID id,
            String productId,
            double pricePerItem,
            int originalAmount,
            int filledAmount,
            int claimedAmount,
            int flippedAmount,
            long placedAt,
            OrderStatus status,
            boolean coopOrder
    ) implements BazaarActivityRecord {
        static final MapCodec<BuyOrderActivity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(BuyOrderActivity::id),
                Codec.STRING.fieldOf("productId").forGetter(BuyOrderActivity::productId),
                Codec.DOUBLE.fieldOf("pricePerItem").forGetter(BuyOrderActivity::pricePerItem),
                Codec.INT.fieldOf("originalAmount").forGetter(BuyOrderActivity::originalAmount),
                Codec.INT.fieldOf("filledAmount").forGetter(BuyOrderActivity::filledAmount),
                Codec.INT.fieldOf("claimedAmount").forGetter(BuyOrderActivity::claimedAmount),
                Codec.INT.optionalFieldOf("flippedAmount", 0).forGetter(BuyOrderActivity::flippedAmount),
                Codec.LONG.fieldOf("placedAt").forGetter(BuyOrderActivity::placedAt),
                OrderStatus.CODEC.fieldOf("status").forGetter(BuyOrderActivity::status),
                Codec.BOOL.fieldOf("coopOrder").forGetter(BuyOrderActivity::coopOrder)
        ).apply(i, BuyOrderActivity::new));

        @Override
        public long recordedAt() {
            return placedAt;
        }

        public int playerClaimedAmount() {
            return claimedAmount - flippedAmount;
        }

        public int unfilledAmount() {
            return originalAmount - filledAmount;
        }

        /**
         * Volume that has been filled but not yet claimed.
         * Mirrors {@link Order#unclaimedFilled()}.
         * Note this includes {@link #flippedAmount} — use {@link #playerClaimedAmount()} if you
         * want only volume the player physically retrieved.
         */
        public int unclaimedFilled() {
            return filledAmount - claimedAmount;
        }

        /**
         * Whether this order has reached a terminal state.
         * Terminal means no further transitions are possible: either fully claimed or cancelled.
         * A {@link OrderStatus.Filled} order is NOT terminal — it still needs a claim or flip.
         */
        public boolean isTerminal() {
            return status instanceof OrderStatus.Claimed || status instanceof OrderStatus.Cancelled;
        }
    }

    /**
     * A sell offer placed directly by the player — not the product of a flip.
     *
     * <p>When cancelled from {@link OrderStatus.Set} or {@link OrderStatus.Partial},
     * the unfilled volume ({@link #unfilledAmount()}) is physically returned to the
     * player's inventory or stash. {@link #returnedAmount()} is non-zero only in
     * this case. A fully-filled sell offer that is claimed has no returned items —
     * the player received coins instead.
     *
     * <p>Sell offers are not exported to cost-basis integrations (e.g. skyblock.finance)
     * because listing items for sale is not an acquisition event. Cancelled sell offers
     * are also excluded — the items were already owned before the offer was placed.
     */
    record SellOfferActivity(
            UUID id,
            String productId,
            double pricePerItem,
            int originalAmount,
            int filledAmount,
            int claimedAmount,
            long placedAt,
            OrderStatus status,
            boolean coopOrder
    ) implements BazaarActivityRecord {
        static final MapCodec<SellOfferActivity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(SellOfferActivity::id),
                Codec.STRING.fieldOf("productId").forGetter(SellOfferActivity::productId),
                Codec.DOUBLE.fieldOf("pricePerItem").forGetter(SellOfferActivity::pricePerItem),
                Codec.INT.fieldOf("originalAmount").forGetter(SellOfferActivity::originalAmount),
                Codec.INT.fieldOf("filledAmount").forGetter(SellOfferActivity::filledAmount),
                Codec.INT.fieldOf("claimedAmount").forGetter(SellOfferActivity::claimedAmount),
                Codec.LONG.fieldOf("placedAt").forGetter(SellOfferActivity::placedAt),
                OrderStatus.CODEC.fieldOf("status").forGetter(SellOfferActivity::status),
                Codec.BOOL.fieldOf("coopOrder").forGetter(SellOfferActivity::coopOrder)
        ).apply(i, SellOfferActivity::new));

        @Override
        public long recordedAt() {
            return placedAt;
        }

        public int unfilledAmount() {
            return originalAmount - filledAmount;
        }

        public int returnedAmount() {
            return status instanceof OrderStatus.Cancelled ? unfilledAmount() : 0;
        }

        /**
         * Volume that has been filled but not yet claimed.
         * Mirrors {@link Order#unclaimedFilled()}.
         */
        public int unclaimedFilled() {
            return filledAmount - claimedAmount;
        }

        /**
         * Whether this order has reached a terminal state.
         * Terminal means no further transitions are possible: either fully claimed or cancelled.
         * A {@link OrderStatus.Filled} order is NOT terminal — it still needs a claim or flip.
         */
        public boolean isTerminal() {
            return status instanceof OrderStatus.Claimed || status instanceof OrderStatus.Cancelled;
        }
    }

    /**
     * A sell offer synthesized as the second half of a flip.
     *
     * <p>{@code sourceId} links back to the {@link BuyOrderActivity} whose fill funded
     * this offer — available for integrations that want to render the full buy→flip→sell
     * arc. It is not required for cost-basis calculation.
     *
     * <p>{@code sourcePricePerItem} is the cost basis captured at flip time directly from
     * the source buy order. It is self-contained — no cross-record lookup is needed. If
     * this offer is later cancelled, {@link #returnedAmount()} units are valued at this
     * price, not at {@code pricePerItem} (the intended sell price).
     */
    record FlipSellActivity(
            UUID id,
            UUID sourceId,
            double sourcePricePerItem,
            String productId,
            double pricePerItem,
            int originalAmount,
            int filledAmount,
            int claimedAmount,
            long placedAt,
            OrderStatus status,
            boolean coopOrder
    ) implements BazaarActivityRecord {
        static final MapCodec<FlipSellActivity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(FlipSellActivity::id),
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("sourceId").forGetter(FlipSellActivity::sourceId),
                Codec.DOUBLE.fieldOf("sourcePricePerItem").forGetter(FlipSellActivity::sourcePricePerItem),
                Codec.STRING.fieldOf("productId").forGetter(FlipSellActivity::productId),
                Codec.DOUBLE.fieldOf("pricePerItem").forGetter(FlipSellActivity::pricePerItem),
                Codec.INT.fieldOf("originalAmount").forGetter(FlipSellActivity::originalAmount),
                Codec.INT.fieldOf("filledAmount").forGetter(FlipSellActivity::filledAmount),
                Codec.INT.fieldOf("claimedAmount").forGetter(FlipSellActivity::claimedAmount),
                Codec.LONG.fieldOf("placedAt").forGetter(FlipSellActivity::placedAt),
                OrderStatus.CODEC.fieldOf("status").forGetter(FlipSellActivity::status),
                Codec.BOOL.fieldOf("coopOrder").forGetter(FlipSellActivity::coopOrder)
        ).apply(i, FlipSellActivity::new));

        @Override
        public long recordedAt() {
            return placedAt;
        }

        public int unfilledAmount() {
            return originalAmount - filledAmount;
        }

        public int returnedAmount() {
            return status instanceof OrderStatus.Cancelled ? unfilledAmount() : 0;
        }

        /**
         * Volume that has been filled but not yet claimed.
         * Mirrors {@link Order#unclaimedFilled()}.
         */
        public int unclaimedFilled() {
            return filledAmount - claimedAmount;
        }

        /**
         * Whether this order has reached a terminal state.
         * Terminal means no further transitions are possible: either fully claimed or cancelled.
         * A {@link OrderStatus.Filled} order is NOT terminal — it still needs a claim or flip.
         */
        public boolean isTerminal() {
            return status instanceof OrderStatus.Claimed || status instanceof OrderStatus.Cancelled;
        }
    }

    /**
     * A stable string key identifying the structural shape of this record.
     * Used for command filtering and autocomplete — not for persistence (the codec
     * uses the same strings, but independently).
     */
    default String type() {
        return switch (this) {
            case InstantBuy ignored -> "instant_buy";
            case InstantSell ignored -> "instant_sell";
            case BuyOrderActivity ignored -> "buy_order";
            case SellOfferActivity ignored -> "sell_offer";
            case FlipSellActivity ignored -> "flip";
        };
    }

    /**
     * A compact human-readable summary of this record, suitable for chat output.
     * Follows the same convention as {@link Order#describe()}.
     * Status timestamps are rendered via {@link OrderStatus#describe()}.
     */
    default String describe() {
        return switch (this) {
            case InstantBuy buy ->
                    "[instant_buy] %s %dx@%.4f".formatted(buy.productId(), buy.volume(), buy.pricePerUnit());
            case InstantSell sell ->
                    "[instant_sell] %s %dx@%.4f".formatted(sell.productId(), sell.volume(), sell.pricePerUnit());
            case BuyOrderActivity buy ->
                    "[buy_order] %s %dx@%.4f filled=%d claimed=%d flipped=%d %s".formatted(
                            buy.productId(), buy.originalAmount(), buy.pricePerItem(),
                            buy.filledAmount(), buy.claimedAmount(), buy.flippedAmount(),
                            buy.status().describe());
            case SellOfferActivity sell ->
                    "[sell_offer] %s %dx@%.4f filled=%d claimed=%d %s".formatted(
                            sell.productId(), sell.originalAmount(), sell.pricePerItem(),
                            sell.filledAmount(), sell.claimedAmount(),
                            sell.status().describe());
            case FlipSellActivity flip ->
                    "[flip] %s %dx@%.4f src=%s %s".formatted(
                            flip.productId(), flip.originalAmount(), flip.pricePerItem(),
                            flip.sourceId().toString().substring(0, 8),
                            flip.status().describe());
        };
    }
}