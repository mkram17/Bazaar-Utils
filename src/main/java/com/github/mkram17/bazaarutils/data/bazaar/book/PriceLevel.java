package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One price's aggregate state in the order book: total volume, the number of
 * distinct orders resting there, and the {@link BazaarDataOrigin} of whichever
 * write most recently confirmed those figures.
 */
public record PriceLevel<O extends BazaarDataOrigin>(
        double pricePerUnit,
        long totalVolume,
        int orderCount,
        @NotNull O origin
) {
    public PriceLevel {
        if (!Double.isFinite(pricePerUnit) || pricePerUnit < 0.0) {
            throw new IllegalArgumentException("PriceLevel pricePerUnit must be finite and non-negative: " + pricePerUnit);
        }
        if (totalVolume < 0) {
            throw new IllegalArgumentException("PriceLevel totalVolume must be non-negative: " + totalVolume);
        }
        if (orderCount < 0) {
            throw new IllegalArgumentException("PriceLevel orderCount must be non-negative: " + orderCount);
        }
        Objects.requireNonNull(origin, "origin");
    }

    /**
     * Returns a copy with {@code amount} added to volume and the order count
     * raised by one, stamped with a fresh origin, the confirmation
     * that one more order now occupies this price.
     */
    public PriceLevel<BazaarDataOrigin.UserPositionEvent> withPlacementIncrement(long amount, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Placement increment amount must be positive: " + amount);
        }

        return new PriceLevel<>(pricePerUnit, totalVolume + amount, orderCount + 1, origin);
    }

    /**
     * Returns a copy with volume reduced by {@code amount}, floored at zero,
     * and stamped with {@code origin}.
     *
     * <p>{@code terminal} marks a decrement that removes an order from this
     * price entirely — a completed fill, a cancel, an expiry — as opposed to
     * an ordinary partial fill, which leaves the order still resting here.
     * Reaching zero volume always clears {@code orderCount} to {@code 0}
     * regardless of {@code terminal}, since an empty price level cannot hold
     * live orders.
     */
    public PriceLevel<BazaarDataOrigin.UserPositionEvent> withVolumeDecrement(long amount, boolean terminal, @NotNull BazaarDataOrigin.UserPositionEvent origin) {
        if (amount < 0) {
            throw new IllegalArgumentException("Volume decrement amount must be non-negative: " + amount);
        }

        long newVolume = Math.max(0L, totalVolume - amount);
        int newCount = newVolume == 0L ? 0 : (terminal ? Math.max(0, orderCount - 1) : orderCount);

        return new PriceLevel<>(pricePerUnit, newVolume, newCount, origin);
    }

    /**
     * Returns {@code true} when this level lies past {@code boundary} in the
     * direction a walk over {@code type}'s side is allowed to travel,
     * signaling that the walk should stop rather than consume it.
     */
    public boolean exceedsBoundary(PriceType type, double boundary) {
        return !type.atLeastAsGood(pricePerUnit, boundary);
    }

    /**
     * Returns {@code true} when {@code other} describes the same substantive market
     * state as this level — equal volume and equal order count. Deliberately
     * narrower than {@link #equals}, which also compares {@code pricePerUnit} and
     * the full {@link BazaarDataOrigin}: two levels can carry different origins —
     * a different poll, a different timestamp — and still agree about what the
     * market currently looks like, and it's specifically that narrower agreement,
     * not provenance, this method is for.
     *
     * <p>A genuine equivalence relation on that narrower substance: reflexive,
     * symmetric, and, being plain field comparison, trivially transitive. Unlike
     * {@link LevelReconciliation#prevailing}, which decide a winner and is not
     * generally symmetric, this one only ever answers "same or different,"
     * the same way regardless of which side calls it.
     */
    public boolean agreesWith(PriceLevel<?> other) {
        return this.totalVolume() == other.totalVolume() && this.orderCount() == other.orderCount();
    }

    /**
     * Returns {@code true} when {@code incoming} carries fresher or higher-authority
     * provenance than {@code existing} in {@code base} — even if both agree on volume
     * and order count. Advancing {@code base}'s origin keeps timestamps fresh so stale
     * overlays can be retired once a newer snapshot clears their grace window.
     */
    public static boolean canAdvanceOrigin(
            PriceLevel<? extends BazaarDataOrigin.Snapshot> existing,
            PriceLevel<? extends BazaarDataOrigin.Snapshot> incoming) {
        if (!incoming.origin().outranks(existing.origin())) return false;

        return incoming.origin().timestamp() > existing.origin().timestamp()
                || (existing.origin() instanceof BazaarDataOrigin.PageSummary
                    && incoming.origin() instanceof BazaarDataOrigin.ApiSnapshot);
    }
}
