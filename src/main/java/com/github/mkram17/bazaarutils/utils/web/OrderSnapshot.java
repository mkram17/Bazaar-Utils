package com.github.mkram17.bazaarutils.utils.web;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One order, flattened for the wire.
 *
 * <p>A deliberate stand-in for {@link Order} rather than a view of it. {@code Order} extends
 * {@code OrderInfo} extends {@code PriceInfo}, implements {@code AbstractListener}, is subscribed
 * to the global event bus, and holds an {@code ItemInfo} wrapping a live {@code ItemStack} — and
 * the storage Gson has an {@code ItemStack} codec registered, so serializing one directly would
 * quietly push full item NBT to the server.</p>
 *
 * <p>Serialized by {@link WebJson}, so this declaration <em>is</em> the wire format: component
 * names become the JSON keys, enums their {@code name()}, and a null {@code pricingPosition} is
 * omitted rather than sent. Names and value domains mirror {@code orderSnapshotSchema} on the
 * website, where a mismatch is rejected with a 400 rather than silently coerced — so renaming a
 * component here is a protocol change.</p>
 */
public record OrderSnapshot(
        String productId,
        String itemName,
        TransactionType.Side side,
        OrderStatus status,
        int volume,
        double pricePerItem,
        int amountFilled,
        int amountClaimed,
        @Nullable PricingPosition pricingPosition,
        String profileId
) {
    private static final int MAX_STRING_LENGTH = 128;

    /**
     * SkyBlock profiles are not tracked by the mod yet (see the plan's decision 1). The column is
     * non-null on the server and defaults to this, meaning "pushed by a mod without profile
     * support" — sending it explicitly makes the eventual switch a one-line change here rather
     * than a schema migration there.
     */
    private static final String UNKNOWN_PROFILE_ID = "";

    /**
     * Converts a tracked order, or empty when it cannot satisfy the server's schema.
     *
     * <p>Orders are parsed out of item lore, so a field can legitimately be missing: an unresolved
     * product ID, a volume the parser returned {@code -1} for. Dropping those one at a time keeps
     * a single unparseable order from costing the whole sync a 400. Callers are expected to report
     * how many were dropped — see {@code OrderSyncService}.</p>
     */
    public static Optional<OrderSnapshot> of(Order order) {
        String productId = order.getProductID();
        String itemName = order.getName();

        if (isUnusable(productId) || isUnusable(itemName)) {
            return Optional.empty();
        }

        TransactionType transactionType = order.getTransactionType();

        if (transactionType == null || transactionType.getSide() == null) {
            return Optional.empty();
        }

        Integer volume = order.getVolume();

        if (volume == null || volume <= 0) {
            return Optional.empty();
        }

        Double pricePerItem = order.getPricePerItem();

        if (pricePerItem == null || !Double.isFinite(pricePerItem) || pricePerItem < 0) {
            return Optional.empty();
        }

        // The lore parsers use -1 for "could not read this", which the server rejects as negative.
        // Clamping is right here: a filled amount we failed to read is best reported as zero
        // progress rather than dropping an order that is otherwise fully described.
        int amountFilled = Math.max(0, Math.min(order.getAmountFilled(), volume));
        int amountClaimed = Math.max(0, Math.min(order.getAmountClaimed(), amountFilled));

        OrderStatus status = order.getStatus() != null ? order.getStatus() : OrderStatus.SET;

        return Optional.of(new OrderSnapshot(
                productId,
                itemName,
                transactionType.getSide(),
                status,
                volume,
                pricePerItem,
                amountFilled,
                amountClaimed,
                // Derived from live market data, so it is stale the moment it lands. The dashboard
                // renders it as "as of last sync" rather than as a live signal. Null when the
                // market data needed to compute it was unavailable, and omitted from the payload.
                order.getPricingPosition(),
                UNKNOWN_PROFILE_ID
        ));
    }

    private static boolean isUnusable(String value) {
        return value == null || value.isBlank() || value.length() > MAX_STRING_LENGTH;
    }
}
