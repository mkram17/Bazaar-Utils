package com.github.mkram17.bazaarutils.data.bazaar.book;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of resolving one price against both of {@link ProductData}'s
 * layers: what base holds there, what overlay holds there, and which of the
 * two — see {@link #prevailing()} — currently speaks for it.
 */
public record LevelReconciliation(
        double pricePerUnit,
        @Nullable PriceLevel base,
        @Nullable PriceLevel overlay
) implements Comparable<LevelReconciliation> {

    public LevelReconciliation {
        // A price with no opinion from either layer isn't a reconciliation at all.
        if (base == null && overlay == null) {
            throw new IllegalArgumentException("LevelReconciliation requires at least one of base/overlay, at price " + pricePerUnit);
        }
    }

    /** Tags whichever of the two layers {@link #prevailing()} resolves to. */
    public enum Layer { BASE, OVERLAY }

    /**
     * Returns whichever of {@code base}/{@code overlay} currently has
     * standing, or {@code null} when both are absent. {@link ProductData}
     * calls this form directly in a couple of places — seeding a write, and
     * checking whether a freshly written base value has already beaten an
     * overlay entry — where building a full {@link LevelReconciliation} only
     * to discard it would be wasted work.
     *
     * <p>When only one side is present, that side wins outright. When both
     * are present, {@code base}'s origin is always a
     * {@link BazaarDataOrigin.Snapshot} by {@link ProductData}'s own
     * construction, so the decision is exactly
     * {@link BazaarDataOrigin.Snapshot#outranks} applied to overlay's origin.
     */
    public static @Nullable PriceLevel prevailing(@Nullable PriceLevel base, @Nullable PriceLevel overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;

        var snap = (BazaarDataOrigin.Snapshot) base.origin();

        return snap.outranks(overlay.origin()) ? base : overlay;
    }

    /**
     * The layer currently speaking for {@link #pricePerUnit}. Never
     * {@code null}: the canonical constructor already rules out the one
     * combination that would leave neither layer with an answer.
     */
    public @NotNull PriceLevel prevailing() {
        return Objects.requireNonNull(prevailing(base, overlay));
    }

    /** Which layer {@link #prevailing()} was drawn from. */
    public @NotNull Layer prevailingLayer() {
        return prevailing() == base ? Layer.BASE : Layer.OVERLAY;
    }

    /** Returns {@code true} when {@link #prevailing()} carries volume worth trading against. */
    public boolean hasLiquidity() {
        return prevailing().totalVolume() > 0;
    }

    /** {@link #prevailing()}, but only when it {@link #hasLiquidity()} — empty otherwise. */
    public @NotNull Optional<PriceLevel> tradable() {
        return hasLiquidity() ? Optional.of(prevailing()) : Optional.empty();
    }

    /**
     * Returns {@code true} when this price has been deliberately emptied by
     * the player's own confirmed activity: overlay currently prevails and
     * carries no volume. This is a positive fact about the price, not merely
     * an absence of one — a fill or a cancel is what put it here, and it
     * stands until a market read earns standing to clear it. This method
     * asks specifically whether the player's own record is the one saying
     * "empty," not whether the figure happens to be zero.
     */
    public boolean isVacated() {
        return prevailingLayer() == Layer.OVERLAY && !hasLiquidity();
    }

    /**
     * Returns {@code true} when base and overlay currently tell a different
     * story about this price's substance.
     */
    public boolean layersDisagree() {
        return base != null && overlay != null && !base.agreesWith(overlay);
    }

    /**
     * Orders ascending by {@link #pricePerUnit} — the same convention
     * {@link ProductData}'s own ask-side maps use; reverse it for a bid-side
     * ordering. Not used by {@link ProductData} itself, which sorts by
     * {@link java.util.TreeMap} key rather than by comparing instances of
     * this type, but available to a caller flattening a combined ask-and-bid
     * view.
     */
    @Override
    public int compareTo(LevelReconciliation other) {
        return Double.compare(pricePerUnit, other.pricePerUnit);
    }
}