package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Returns the current order list, or {@code null} when no profile is loaded.
 * Logs an error with the origin description on miss. Callers must guard against {@code null}
 * and return early before any resolution that reads the order list.
 */
public abstract class ChatOrderSource extends BUListener {
    @Nullable
    protected final List<Order> requireStorage(BazaarDataOrigin origin) {
        var storage = UserOrdersStorage.get();

        if (storage == null) {
            PlayerLogger.sendError(origin.describe() + " skipped — profile storage not loaded", new Throwable());
        }

        return storage;
    }

    /**
     * Applies a resolved {@link OrderDelta} to storage and the book.
     *
     * <p>Owns the storage write, reindex strategy, event dispatch, and persist call.
     * Callers produce a delta and hand it here — nothing beyond that point is their
     * concern.
     */
    protected final void commit(OrderDelta delta, BazaarDataOrigin.UserPositionEvent origin) {
        switch (delta) {

            case OrderDelta.None ignored -> {}

            case OrderDelta.BookOnly book -> {
                book.mutation().apply(book.productId(), origin);

                new BazaarDataUpdateEvent(book.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Place placement -> {
                placement.mutation().apply(placement.productId(), origin);

                var result = UserOrdersStorage.apply(
                        UserOrdersStorage.StorageOp.add(placement.order())
                                .then(UserOrdersStorage.StorageOp.reindex()));

                var placed = UserOrdersStorage.findById(result, placement.order().id())
                        .orElse(placement.order());

                new UserOrderEvent.Placed(placed).post(BazaarUtils.EVENT_BUS);

                var fillEvent = placement.initialFillEvent();
                if (fillEvent != null) fillEvent.post(BazaarUtils.EVENT_BUS);

                new BazaarDataUpdateEvent(placement.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Update update -> {
                update.mutation().apply(update.productId(), origin);

                var result = UserOrdersStorage.apply(
                        UserOrdersStorage.StorageOp.replace(update.before(), ignored -> update.after())
                                .then(UserOrdersStorage.StorageOp.reindexIfOffsets(update.before(), update.after())));

                var resolved = UserOrdersStorage.findById(result, update.before().id())
                        .orElse(update.after());

                update.kind().getEvent(update.before(), resolved).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(update.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Evict eviction -> {
                eviction.mutation().apply(eviction.productId(), origin);

                UserOrdersStorage.apply(
                        UserOrdersStorage.StorageOp.cancel(eviction.order(), origin)
                                .then(UserOrdersStorage.StorageOp.reindex()));

                new UserOrderEvent.Cancelled(eviction.order()).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(eviction.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Swap swap -> {
                // Sell book: place the new sell level.
                // Buy book needs no mutation — the buy level was already decremented
                // when the buy order was originally filled.
                swap.mutation().apply(swap.productId(), origin);

                var result = UserOrdersStorage.apply(
                        UserOrdersStorage.StorageOp.replace(swap.buyBefore(), ignored -> swap.buyAfter())
                                .then(UserOrdersStorage.StorageOp.add(swap.newSell()))
                                .then(UserOrdersStorage.StorageOp.reindex()));

                var inserted = UserOrdersStorage.findById(result, swap.newSell().id()).orElse(swap.newSell());

                new UserOrderEvent.Flipped(swap.buyAfter(), inserted).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(swap.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            default -> throw new IllegalStateException("Unexpected value: " + delta);
        }
    }
}