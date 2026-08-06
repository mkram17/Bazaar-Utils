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
    /**
     * Longest {@code productId} or {@code itemName} the website accepts. Package-private rather
     * than private so {@code WireFormatContractTest} can hold it to
     * {@code contract/wire-format.json}.
     */
    static final int MAX_STRING_LENGTH = 128;

    /**
     * Longest {@code profileId} the website accepts. A profile whose name somehow exceeds it is
     * sent unlabelled rather than rejected: a 400 would cost the whole sync, not one field.
     */
    static final int MAX_PROFILE_ID_LENGTH = 64;

    /**
     * What the server reads as "pushed by a mod that does not know its profile". The column is
     * non-null there and defaults to this, so it stays the value for a snapshot collected before
     * any {@code ProfileChangeEvent} has been seen.
     */
    private static final String UNKNOWN_PROFILE_ID = "";

    /**
     * Converts a tracked order, or empty when it cannot satisfy the server's schema.
     *
     * <p>Orders are parsed out of item lore, so a field can legitimately be missing: an unresolved
     * product ID, a volume the parser returned {@code -1} for. Dropping those one at a time keeps
     * a single unparseable order from costing the whole sync a 400. Callers are expected to report
     * how many were dropped — see {@code OrderSyncService}.</p>
     *
     * @param profileId the SkyBlock profile these orders were loaded under. Orders are stored per
     *                  profile, so an unlabelled snapshot merges every profile the player has into
     *                  one bucket — and since absence is the server's close signal, syncing after a
     *                  profile switch then closes the orders belonging to the profile just left.
     */
    public static Optional<OrderSnapshot> of(Order order, String profileId) {
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
                sendableProfileId(profileId)
        ));
    }

    private static String sendableProfileId(String profileId) {
        return profileId == null || profileId.isBlank() || profileId.length() > MAX_PROFILE_ID_LENGTH
                ? UNKNOWN_PROFILE_ID
                : profileId;
    }

    private static boolean isUnusable(String value) {
        return value == null || value.isBlank() || value.length() > MAX_STRING_LENGTH;
    }
}
