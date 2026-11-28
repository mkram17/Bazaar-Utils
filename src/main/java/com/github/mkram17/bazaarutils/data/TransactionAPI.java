package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.TransactionConfirmationParser;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.TransactionPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the {@link OrderInfo} parsed from the most recently seen order confirmation screen,
 * and the {@link PendingFlip} parsed from the most recently seen flip price sign.
 *
 * <p>The confirmation screen always shows the exact fractional price per unit that Hypixel records,
 * whereas the chat placement message may round large-order totals.
 * {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver#resolveForPlacement}
 * consumes this to prefer the precise confirmation price over the chat-derived approximation. The
 * flip price sign is the same idea applied to flips:
 * {@link com.github.mkram17.bazaarutils.data.bazaar.pipeline.OrderResolver#resolveForFlip}
 * prefers it over the flip chat message's own rounding.
 *
 * <p>Cleared via {@link #consume()} / {@link #consumeFlip()}, on re-entering price/orders screens,
 * on failed confirmation parses, and on profile switches — not on container close, because the
 * chat confirmation message arrives after the screen has already dismissed.
 */
@Module
public final class TransactionAPI extends BUListener {
    private static final AtomicReference<@Nullable OrderInfo> PENDING = new AtomicReference<>();
    private static final AtomicReference<@Nullable PendingFlip> PENDING_FLIP = new AtomicReference<>();

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.BUY_ORDER_CONFIRMATION)
    private void onBuyConfirmationScreen(ContainerLoadedEvent event) {
        ScreenContext context = event.asContext();

        TransactionPageLayout.getConfirmBuyOrderItem(context)
                .map(ItemInfo::itemStack)
                .flatMap(TransactionConfirmationParser::parseBuyOrder)
                .ifPresentOrElse(
                        info -> {
                            PENDING.set(info);
                            PlayerActionUtil.notifyAll("Buy confirmation captured: %s %dx@%.4f".formatted(info.getName(), info.getVolume(), info.getPricePerItem()), NotificationType.GUI);
                        },
                        () -> {
                            clear();
                            Util.logMessage("Buy confirmation screen visible but parse produced no result");
                        }
                );
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.SELL_OFFER_CONFIRMATION)
    private void onSellConfirmationScreen(ContainerLoadedEvent event) {
        ScreenContext context = event.asContext();

        TransactionPageLayout.getConfirmSellOfferItem(context)
                .map(ItemInfo::itemStack)
                .flatMap(TransactionConfirmationParser::parseSellOffer)
                .ifPresentOrElse(
                        info -> {
                            PENDING.set(info);
                            PlayerActionUtil.notifyAll("Sell confirmation captured: %s %dx@%.4f".formatted(info.getName(), info.getVolume(), info.getPricePerItem()), NotificationType.GUI);
                        },
                        () -> {
                            clear();
                            Util.logMessage("Sell confirmation screen visible but parse produced no result");
                        }
                );
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(value = BazaarScreenType.COMPLETED_BUY_ORDER_FLIP_PRICE_INPUT, previous = true)
    private void onFlipPriceInputClosed(ScreenChangeEvent.Post event) {
        ScreenContext context = ScreenManager.getInstance().find(event.getOldScreen()).orElse(null);

        Optional<Double> priceInput = Optional.ofNullable(context)
                .flatMap(TransactionPageLayout::getFlipSignLines)
                .flatMap(TransactionConfirmationParser::parseFlipPrice);

        Optional<Order> matchedBuy = HandledOrderAPI.getForOptions()
                .filter(Order::isFlippable);

        Optional<PendingFlip> flip = priceInput.flatMap(pricePerUnit -> matchedBuy.map(buy -> new PendingFlip(buy, pricePerUnit)));

        PENDING_FLIP.set(flip.orElse(null));

        flip.ifPresentOrElse(
                f -> PlayerActionUtil.notifyAll("Flip price captured: %s %dx@%.4f".formatted(f.matchedBuy().productId(), f.matchedBuy().unclaimedFilled(), f.sellPrice()), NotificationType.GUI),
                () -> Util.logMessage("Flip price sign closed but price=%s matchedBuy=%s".formatted(priceInput, matchedBuy))
        );
    }

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen({BazaarScreenType.BUY_ORDER_PRICE, BazaarScreenType.SELL_OFFER_PRICE})
    private void onTransactionDetails(ContainerLoadedEvent ignored) {
        clear();
    }

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    private void onOrdersPageOpen(ContainerLoadedEvent ignored) {
        clearFlip();
    }

    @Subscription(priority = Priority.HIGH)
    private void onProfileChange(ProfileChangeEvent ignored) {
        clear();
        clearFlip();
    }

    /**
     * Returns the most recently parsed confirmation order, if any.
     * The value remains until {@link #consume()} is called.
     */
    public static Optional<OrderInfo> get() {
        return Optional.ofNullable(PENDING.get());
    }

    /**
     * Returns and clears the pending order in one atomic step.
     */
    public static Optional<OrderInfo> consume() {
        OrderInfo value = PENDING.getAndSet(null);
        if (value != null) Util.logMessage("Order transaction consumed: " + value);

        return Optional.ofNullable(value);
    }

    /**
     * Returns the most recently parsed flip price, if any.
     * The value remains until {@link #consumeFlip()} is called.
     */
    public static Optional<PendingFlip> getFlip() {
        return Optional.ofNullable(PENDING_FLIP.get());
    }

    /**
     * Returns and clears the pending flip price in one atomic step.
     */
    public static Optional<PendingFlip> consumeFlip() {
        PendingFlip value = PENDING_FLIP.getAndSet(null);
        if (value != null) Util.logMessage("Flip price consumed: " + value);

        return Optional.ofNullable(value);
    }

    private static void clear() {
        OrderInfo value = PENDING.getAndSet(null);
        if (value != null) Util.logMessage("Pending transaction cleared: " + value);
    }

    private static void clearFlip() {
        PendingFlip value = PENDING_FLIP.getAndSet(null);
        if (value != null) Util.logMessage("Pending flip cleared: " + value);
    }

    /**
     * Pairs the buy order a flip-price sign belongs to with the exact price the
     * player typed on it.
     */
    public record PendingFlip(@NotNull Order matchedBuy, double sellPrice) {}
}
