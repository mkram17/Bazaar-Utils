package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.data.stored.ProfileKey;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.bazaar.data.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.events.bazaar.UserOrderEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Base for sources that resolve a Bazaar chat message into an
 * {@link OrderDelta} and commit it.
 *
 * <p>Every concrete chat source resolves its own event into a delta and
 * hands it to {@link #commit} — nothing past that point is the source's
 * concern.
 */
public abstract class ChatOrderSource extends BUListener {
    /**
     * Commits a delta that never touches order storage — {@link OrderDelta.None}
     * or {@link OrderDelta.BookOnly} only. Rejects anything else: those variants
     * need a {@link ProfileKey} to write storage with, and this overload
     * structurally has none to give — a caller reaching this with the wrong
     * delta type has a bug in its own construction, not something to guess past.
     */
    protected final void commit(OrderDelta<BazaarDataOrigin.UserPositionEvent> delta, BazaarDataOrigin.UserPositionEvent origin) {
        switch (delta) {
            case OrderDelta.None<BazaarDataOrigin.UserPositionEvent> ignored -> {}

            case OrderDelta.BookOnly<BazaarDataOrigin.UserPositionEvent> book -> {
                book.mutation().apply(book.productId(), origin);

                FillInference.settle(book.productId(), origin);

                new BazaarDataUpdateEvent(book.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            default -> throw new IllegalArgumentException(
                    "commit(delta, origin) supports only None/BookOnly — " + delta.getClass().getSimpleName()
                            + " needs a resolved ProfileKey; use commit(delta, origin, key).");
        }
    }

    /**
     * Commits a resolved {@link OrderDelta} for {@code key}: applies its book
     * mutation, writes storage via {@link FillInference#settle} (settling every
     * known position on the touched product before returning), fires whichever
     * {@link UserOrderEvent} the variant implies — tagged with {@code key},
     * supplied here, never resolved internally — and posts
     * {@link BazaarDataUpdateEvent}.
     */
    protected final void commit(OrderDelta<BazaarDataOrigin.UserPositionEvent> delta, BazaarDataOrigin.UserPositionEvent origin, @NotNull ProfileKey key) {
        if (delta instanceof OrderDelta.None<BazaarDataOrigin.UserPositionEvent> || delta instanceof OrderDelta.BookOnly<BazaarDataOrigin.UserPositionEvent>) {
            commit(delta, origin);

            return;
        }

        switch (delta) {
            case OrderDelta.Place<BazaarDataOrigin.UserPositionEvent> placement -> {
                placement.mutation().apply(placement.productId(), origin);

                var result = FillInference.settle(
                        key, placement.productId(), origin,
                        UserOrdersStorage.StorageOp.add(placement.order())
                                .then(UserOrdersStorage.StorageOp.reindex()));

                var placed = UserOrdersStorage.findById(result, placement.order().id())
                        .orElse(placement.order());

                new UserOrderEvent.Placed(placed, key).post(BazaarUtils.EVENT_BUS);

                var fillEvent = placement.initialFillEvent(key);
                if (fillEvent != null) fillEvent.post(BazaarUtils.EVENT_BUS);

                new BazaarDataUpdateEvent(placement.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Update<BazaarDataOrigin.UserPositionEvent> update -> {
                update.mutation().apply(update.productId(), origin);

                var result = FillInference.settle(
                        key, update.productId(), origin,
                        UserOrdersStorage.StorageOp.replace(update.before(), ignored -> update.after())
                                .then(UserOrdersStorage.StorageOp.reindexIfOffsets(update.before(), update.after())));

                var resolved = UserOrdersStorage.findById(result, update.before().id())
                        .orElse(update.after());

                update.kind().getEvent(update.before(), resolved, key).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(update.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Evict<BazaarDataOrigin.UserPositionEvent> eviction -> {
                eviction.mutation().apply(eviction.productId(), origin);

                FillInference.settle(
                        key, eviction.productId(), origin,
                        UserOrdersStorage.StorageOp.cancel(eviction.order(), origin)
                                .then(UserOrdersStorage.StorageOp.reindex()));

                new UserOrderEvent.Cancelled(eviction.order(), key).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(eviction.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            case OrderDelta.Swap<BazaarDataOrigin.UserPositionEvent> swap -> {
                swap.mutation().apply(swap.productId(), origin);

                var result = FillInference.settle(
                        key, swap.productId(), origin,
                        UserOrdersStorage.StorageOp.replace(swap.buyBefore(), ignored -> swap.buyAfter())
                                .then(UserOrdersStorage.StorageOp.add(swap.newSell()))
                                .then(UserOrdersStorage.StorageOp.reindex()));

                var inserted = UserOrdersStorage.findById(result, swap.newSell().id()).orElse(swap.newSell());

                new UserOrderEvent.Flipped(swap.buyAfter(), inserted, key).post(BazaarUtils.EVENT_BUS);
                new BazaarDataUpdateEvent(swap.productId(), origin).post(BazaarUtils.EVENT_BUS);
            }

            default -> throw new IllegalStateException("Unexpected value: " + delta);
        }
    }
}