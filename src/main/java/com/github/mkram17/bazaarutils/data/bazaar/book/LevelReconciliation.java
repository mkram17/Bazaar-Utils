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
        @Nullable PriceLevel<? extends BazaarDataOrigin.Snapshot> base,
        @Nullable PriceLevel<? extends BazaarDataOrigin.UserPositionEvent> overlay
) {
    public LevelReconciliation {
        // A price with no opinion from either layer isn't a reconciliation at all.
        if (base == null && overlay == null) {
            throw new IllegalArgumentException("LevelReconciliation requires at least one of base/overlay, at price " + pricePerUnit);
        }
        if (base != null && Double.compare(base.pricePerUnit(), pricePerUnit) != 0) {
            throw new IllegalArgumentException(
                    "LevelReconciliation base price (%s) does not match pricePerUnit (%s)".formatted(base.pricePerUnit(), pricePerUnit));
        }
        if (overlay != null && Double.compare(overlay.pricePerUnit(), pricePerUnit) != 0) {
            throw new IllegalArgumentException(
                    "LevelReconciliation overlay price (%s) does not match pricePerUnit (%s)".formatted(overlay.pricePerUnit(), pricePerUnit));
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
     * are present, {@code base}'s origin is a {@link BazaarDataOrigin.Snapshot}
     * by its declared type so the decision is exactly {@link BazaarDataOrigin.Snapshot#outranks}
     * applied to overlay's origin.
     */
    public static @Nullable PriceLevel<?> prevailing(
            @Nullable PriceLevel<? extends BazaarDataOrigin.Snapshot> base,
            @Nullable PriceLevel<? extends BazaarDataOrigin.UserPositionEvent> overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;

        return base.origin().outranks(overlay.origin()) ? base : overlay;
    }

    /**
     * The layer currently speaking for {@link #pricePerUnit}. Never
     * {@code null}: the canonical constructor already rules out the one
     * combination that would leave neither layer with an answer.
     */
    public @NotNull PriceLevel<?> prevailing() {
        return Objects.requireNonNull(prevailing(base, overlay));
    }

    /** Which layer {@code level} was drawn from. */
    public @NotNull Layer prevailingLayer(@NotNull PriceLevel<?> level) {
        if (level == base) return Layer.BASE;
        if (level == overlay) return Layer.OVERLAY;

        throw new IllegalArgumentException("PriceLevel does not belong to this LevelReconciliation at price " + pricePerUnit);
    }

    /** @see #prevailingLayer(PriceLevel) */
    public @NotNull Layer prevailingLayer() {
        return prevailingLayer(prevailing());
    }

    /** Returns {@code true} when {@code level} carries volume worth trading against. */
    public boolean hasLiquidity(@NotNull PriceLevel<?> level) {
        return level.totalVolume() > 0;
    }

    /** @see #hasLiquidity(PriceLevel) */
    public boolean hasLiquidity() {
        return hasLiquidity(prevailing());
    }

    public @Nullable PriceLevel<?> tradableOrNull() {
        var level = prevailing();

        return hasLiquidity(level) ? level : null;
    }

    /** {@link #prevailing()}, but only when it {@link #hasLiquidity()} — empty otherwise. */
    public @NotNull Optional<PriceLevel<?>> tradable() {
        return Optional.ofNullable(tradableOrNull());
    }

    /**
     * Returns {@code true} when this price has been deliberately emptied by
     * the player's own confirmed activity: overlay currently prevails and
     * carries no volume. This is a positive fact about the price, not merely
     * an absence of one — a fill or a cancel is what put it here, and it
     * stands until a market read earns standing to clear it.
     */
    public boolean isVacated(@NotNull PriceLevel<?> level) {
        return prevailingLayer(level) == Layer.OVERLAY && !hasLiquidity(level);
    }

    /** @see #isVacated(PriceLevel) */
    public boolean isVacated() {
        return isVacated(prevailing());
    }

    /**
     * Returns {@code true} when base and overlay currently tell a different
     * story about this price's substance.
     */
    public boolean layersDisagree() {
        return base != null && overlay != null && !base.agreesWith(overlay);
    }
}
