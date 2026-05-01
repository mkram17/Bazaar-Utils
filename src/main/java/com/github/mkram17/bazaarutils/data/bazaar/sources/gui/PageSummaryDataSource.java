package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageSummaryParser;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ProductPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.List;
import java.util.NavigableMap;

/**
 * Parses the top buy/sell price-level list from the Bazaar item summary screen
 * and splices it into the book store.
 *
 * <p>Screen layout quirk: the "Create Buy Order" item's lore shows the top
 * <em>sell offers</em> (asks), and the "Create Sell Offer" item's lore shows
 * the top <em>buy orders</em> (bids). The swap below is intentional.
 *
 * <p>Single-product per screen load — delegates to {@link #commitProduct}, which
 * calls {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.FillInference#applyAll}
 * immediately (no batching benefit here).
 */
@DataSource
public final class PageSummaryDataSource extends SnapshotSource {
    public PageSummaryDataSource() {}

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.PRODUCT_PAGE)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        var origin = new BazaarDataOrigin.PageSummary(System.currentTimeMillis());
        ScreenContext context = event.asContext();

        var productInfo = ProductPageLayout.getDisplayProductInfo(context).orElse(null);
        if (productInfo == null) return;
        String productId = productInfo.getProductId();

        var buyStack = ProductPageLayout.getCreateBuyOrderItem(context) .map(ItemInfo::itemStack).orElse(null);
        var sellStack = ProductPageLayout.getCreateSellOfferItem(context).map(ItemInfo::itemStack).orElse(null);
        if (buyStack == null || sellStack == null) return;

        // buyStack lore = sell-side (ask) levels → INSTABUY book
        // sellStack lore = buy-side (bid) levels  → INSTASELL book
        var result = PageSummaryParser.parseItemPage(buyStack, sellStack, origin.timestamp());
        if (result.bidLevels().isEmpty() && result.askLevels().isEmpty()) return;

        List<Order> activeOrders = UserOrdersStorage.active();

        boolean changed = commitProduct(
                productId,
                result.askLevels(),
                result.bidLevels(),
                activeOrders,
                NotificationType.ORDERDATA,
                origin);

        PlayerActionUtil.notifyAll("%s: %s — %d buy levels, %d sell levels".formatted(
                origin.describe(), productId,
                result.askLevels().size(), result.bidLevels().size()), NotificationType.GUI);

        if (changed) {
            new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS);
        }

        var data = BazaarDataRegistry.get(productId); if (data == null) return;

        Util.logMessage("%s: %s buyBook=%s sellBook=%s".formatted(origin.describe(), productId, bestPrice(data.getAsksBook()), bestPrice(data.getBidsBook())));
    }

    private static String bestPrice(NavigableMap<Double, PriceLevel> book) {
        var entry = book.firstEntry();
        return entry == null ? "N/A" : String.valueOf(entry.getValue().pricePerUnit());
    }
}