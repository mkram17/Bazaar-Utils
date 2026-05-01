package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.TransactionConfirmationParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.Optional;

/**
 * Holds the {@link OrderInfo} parsed from the most recently seen
 * "Confirm Buy Order" or "Confirm Sell Offer" confirmation screen.
 *
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Populated whenever a confirmation screen update is observed.</li>
 *   <li>Cleared only via {@link #consume()} — intentionally <em>not</em> on
 *       container close, because the chat confirmation message arrives after
 *       the screen has already closed.</li>
 * </ul>
 */
@Module
public final class TransactionAPI extends BUListener {
    @Nullable
    private static OrderInfo pending = null;

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.BUY_ORDER_CONFIRMATION)
    public void onBuyConfirmationScreen(ContainerLoadedEvent event) {
        ScreenContext context = event.asContext();

        TransactionPageLayout.getConfirmBuyOrderItem(context)
                .map(ItemInfo::itemStack)
                .flatMap(TransactionConfirmationParser::parseBuyOrder)
                .ifPresentOrElse(
                        info -> {
                            pending = info;
                            Util.logMessage("Buy confirmation captured: " + info);
                        },
                        () -> Util.logMessage("Buy confirmation screen visible but parse produced no result")
                );
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.SELL_OFFER_CONFIRMATION)
    public void onSellConfirmationScreen(ContainerLoadedEvent event) {
        ScreenContext context = event.asContext();

        TransactionPageLayout.getConfirmSellOfferItem(context)
                .map(ItemInfo::itemStack)
                .flatMap(TransactionConfirmationParser::parseSellOffer)
                .ifPresentOrElse(
                        info -> {
                            pending = info;
                            Util.logMessage("Sell confirmation captured: " + info);
                        },
                        () -> Util.logMessage("Sell confirmation screen visible but parse produced no result")
                );
    }

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen({BazaarScreenType.BUY_ORDER_PRICE, BazaarScreenType.SELL_OFFER_PRICE})
    public void onTransactionDetails(ContainerLoadedEvent event) {
        clear();
    }

    /**
     * Returns the most recently parsed confirmation order, if any.
     * The value remains until {@link #consume()} is called.
     */
    public static Optional<OrderInfo> get() {
        return Optional.ofNullable(pending);
    }

    /**
     * Returns and clears the pending order in one atomic step.
     */
    public static Optional<OrderInfo> consume() {
        OrderInfo value = pending;
        pending = null;
        if (value != null) Util.logMessage("Order transaction consumed: " + value);

        return Optional.ofNullable(value);
    }

    /**
     * Discards any pending order without returning it.
     * Called when re-entering the price screen, indicating a new transaction
     * may be produced and the previously captured confirmation is potentially stale.
     */
    private static void clear() {
        OrderInfo value = pending;
        pending = null;
        if (value != null) Util.logMessage("Pending transaction cleared: " + value);
    }
}