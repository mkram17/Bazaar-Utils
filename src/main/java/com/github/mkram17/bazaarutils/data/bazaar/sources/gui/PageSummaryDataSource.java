package com.github.mkram17.bazaarutils.data.bazaar.sources.gui;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.data.bazaar.book.PriceLevel;
import com.github.mkram17.bazaarutils.data.bazaar.pipeline.SnapshotSource;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.DataSource;
import com.github.mkram17.bazaarutils.utils.bazaar.components.PageSummaryParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ProductPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import java.util.NavigableMap;
import java.util.function.Function;

/**
 * Splices one product page's rendered price levels into the book and infers any fill
 * progress they reveal across every known position, firing on every Product Page
 * container load.
 */
@DataSource
public final class PageSummaryDataSource extends SnapshotSource {
    /**
     * Resolves the displayed product's identity and its two rendered item stacks,
     * parses their lore into ask and bid levels, and commits them via
     * {@link #processProduct} — the direct, unbatched form, since exactly one product
     * arrives per load here.
     *
     * <p>Any read that comes back empty — product not resolvable, either item stack
     * absent, or nothing parsed from the lore — bails out silently before touching
     * storage. {@link BazaarDataUpdateEvent} is posted only when {@link #processProduct}
     * reports a change.
     */
    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.PRODUCT_PAGE)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        var origin = new BazaarDataOrigin.PageSummary(System.currentTimeMillis());

        ScreenContext context = event.asContext();

        var productInfo = ProductPageLayout.getDisplayProductInfo(context).orElse(null);
        if (productInfo == null) return;

        String productId = productInfo.getProductId();

        var buyStack = ProductPageLayout.getCreateBuyOrderItem(context).map(ItemInfo::itemStack).orElse(null);
        var sellStack = ProductPageLayout.getCreateSellOfferItem(context).map(ItemInfo::itemStack).orElse(null);
        if (buyStack == null || sellStack == null) return;

        var parsed = PageSummaryParser.parseItemPage(buyStack, sellStack, origin);
        if (parsed.isEmpty()) return;

        var data = BazaarDataRegistry.getOrCreate(productId);

        boolean changed = processProduct(productId, data, parsed.asksLevels(), parsed.bidsLevels(),
                Function.identity(), origin, NotificationType.ORDERDATA);

        PlayerActionUtil.notifyAll("%s: %s — %d buy levels, %d sell levels".formatted(
                origin.describe(), productId,
                parsed.asksLevels().size(), parsed.bidsLevels().size()), NotificationType.GUI);

        if (changed) {
            new BazaarDataUpdateEvent(productId, origin).post(BazaarUtils.EVENT_BUS);
        }

        Util.logMessage("%s: %s buyBook=%s sellBook=%s".formatted(
                origin.describe(), productId,
                bestPrice(data.tradableLevels(PriceType.INSTABUY)),
                bestPrice(data.tradableLevels(PriceType.INSTASELL))));
    }

    /** Formats the top-of-book price in {@code book}, or {@code "N/A"} if empty — used only for the debug log line above. */
    private static String bestPrice(NavigableMap<Double, PriceLevel> book) {
        var entry = book.firstEntry();

        return entry == null ? "N/A" : String.valueOf(entry.getValue().pricePerUnit());
    }
}