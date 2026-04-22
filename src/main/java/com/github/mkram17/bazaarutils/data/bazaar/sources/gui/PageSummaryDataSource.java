package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.DataSource;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageSummaryParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSources;
import com.github.mkram17.bazaarutils.utils.bazaar.data.PriceLevel;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ItemPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.OrdersPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

/**
 * Parses the top buy/sell price-level list from the Bazaar item summary screen
 * and splices it into the book store.
 *
 * <p>Screen layout quirk: the "Create Buy Order" item's lore shows the top
 * <em>sell offers</em> (asks), and the "Create Sell Offer" item's lore shows
 * the top <em>buy orders</em> (bids). The swap below is intentional.
 */
@DataSource
public final class PageSummaryDataSource extends BUListener {
    private static final BazaarLogger LOG = BazaarLogger.of(PageSummaryDataSource.class);

    public PageSummaryDataSource() {}

    @Subscription(priority = Subscription.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ITEM_PAGE)
    public void onChestLoaded(ChestLoadedEvent event) {
        Optional<ScreenContext> context = ScreenManager.getInstance().current();
        if (context.isEmpty()) return;
        var screen = context.get();

        var productInfo = ItemPageLayout.getDisplayProductInfo(screen).orElse(null);
        if (productInfo == null) return;
        String productId = productInfo.getProductId();

        var buyStack = ItemPageLayout.getCreateBuyOrderItem(screen) .map(ItemInfo::itemStack).orElse(null);
        var sellStack = ItemPageLayout.getCreateSellOfferItem(screen).map(ItemInfo::itemStack).orElse(null);
        if (buyStack == null || sellStack == null) return;

        // buyStack lore  = sell-side (ask) levels → BUY side of book
        // sellStack lore = buy-side (bid) levels  → SELL side of book
        var result = PageSummaryParser.parseItemPage(buyStack, sellStack);
        if (result.bidLevels().isEmpty() && result.askLevels().isEmpty()) return;

        var source = new DataSources.PageSummary(result.observedAt());
        var data = BazaarDataRegistry.getOrCreate(productId);

        boolean changed = data.apply(TransactionType.Side.BUY, result.askLevels(), source);
        changed |= data.apply(TransactionType.Side.SELL, result.bidLevels(), source);

        PlayerLogger.debug("%s: %s — %d buy levels, %d sell levels".formatted(source.describe(), productId, result.askLevels().size(), result.bidLevels().size()), NotificationType.SCREEN_PARSING);

        var storage = UserOrdersStorage.INSTANCE.get();
        if (storage != null) {
            var allInferredFills = new ArrayList<Order>();

            storage.stream()
                    .filter(order -> order.productId().equals(productId))
                    .filter(Order::isActive)
                    .collect(Collectors.groupingBy(order -> Map.entry(order.side(), order.pricePerItem())))
                    .forEach((key, group) -> {
                        var transaction = TransactionType.of(key.getKey(), TransactionType.Method.ORDER);
                        var book = data.bookFor(transaction);
                        var level = book.get(key.getValue());

                        if (level == null || level.orderCount() != 1) return;

                        // Hypixel fills FIFO within a price level — lowest slot index goes first.
                        group.stream()
                                .filter(Order::isAnchored)
                                .min(Comparator.comparingInt(Order::lastKnownIndex))
                                .or(() -> group.stream().findFirst()) // fallback: unanchored order
                                .ifPresent(order -> {
                                    int inferredFilled = order.originalAmount() - (int) level.totalVolume();

                                    if (inferredFilled > order.filledAmount()) {
                                        allInferredFills.add(order.withFill(inferredFilled - order.filledAmount()));

                                        PlayerLogger.debug("%s — Fill advanced %d → %d (sole order, vol=%d): %s".formatted(source.describe(), order.filledAmount(), inferredFilled, (int) level.totalVolume(), order.describe()), NotificationType.ORDER_LIFECYCLE);
                                    }
                                });
                    });

            if (!allInferredFills.isEmpty()) {
                changed = true;

                var fillIds = allInferredFills.stream().map(Order::id).collect(Collectors.toSet());
                var fillMap = allInferredFills.stream().collect(Collectors.toMap(Order::id, order -> order));

                var withFills = storage.stream()
                        .map(order -> fillIds.contains(order.id()) ? fillMap.get(order.id()) : order)
                        .collect(Collectors.toCollection(ArrayList::new));

                var reindexed = OrdersPageLayout.reindexActive(withFills);

                UserOrdersStorage.INSTANCE.set(reindexed);

                reindexed.stream()
                        .filter(order -> fillIds.contains(order.id()))
                        .forEach(order -> {
                            switch (order.status()) {
                                case OrderStatus.Filled ignored -> {
                                    new UserOrderEvent.Filled(order).post(EVENT_BUS);
                                    PlayerLogger.debug("%s — Marked fully filled (inferred): %s".formatted(source.describe(), order.describe()), NotificationType.ORDER_LIFECYCLE);
                                }
                                default -> {
                                    new UserOrderEvent.PartiallyFilled(order).post(EVENT_BUS);
                                    PlayerLogger.debug("%s — Fill advanced (inferred): %s".formatted(source.describe(), order.describe()), NotificationType.ORDER_LIFECYCLE);
                                }
                            }
                        });

                UserOrdersStorage.persist();
            }
        }

        if (changed) {
            new BazaarDataUpdateEvent(productId, source).post(EVENT_BUS);
        }

        LOG.info("{}: {} buyBook={} sellBook={}", source.describe(), productId, bestPrice(data.getBuyBook()), bestPrice(data.getSellBook()));
    }

    private static String bestPrice(NavigableMap<Double, PriceLevel> book) {
        var entry = book.firstEntry();

        return entry == null ? "N/A" : String.valueOf(entry.getValue().pricePerUnit());
    }
}