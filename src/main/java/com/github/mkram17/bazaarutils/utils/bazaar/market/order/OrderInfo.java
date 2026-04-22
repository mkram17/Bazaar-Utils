package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.data.bazaar.BazaarDataRegistry;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Stores Bazaar item information while automatically tracking market pricePerUnit updates and performing
 * health checks on product identifiers. Intended for order-like data that does not need the full
 */
@Getter
@ToString(callSuper=true)
public class OrderInfo extends PriceInfo {
    /**
     * Maximum per-unit coin inaccuracy Hypixel can introduce in displayed prices.
     */
    public static final double COIN_EPSILON = 0.9;

    /**
     * Total order value above which Hypixel starts rounding displayed prices/whereof the epsilon is to be consumed.
     */
    public static final double FOLDING_THRESHOLD = 10000;

    @NotNull
    private final String name;

    @NotNull
    private final int volume;

    @NotNull
    private final double tolerance;

    /**
     * Creates a container that tracks market data for a specific Bazaar product.
     *
     * @param name         display name of the item
     * @param productId    {@link ProductInfo} identifier of the item
     * @param side         whether this is a buy or sell order
     * @param pricePerItem current pricePerUnit per unit for the order
     * @param volume       quantity of the order
     */
    private OrderInfo(
            @NotNull String name,
            @NotNull String productId,
            @NotNull TransactionType.Side side,
            @NotNull Double pricePerItem,
            @NotNull Integer volume) {
        super(productId, TransactionType.of(side, TransactionType.Method.ORDER), pricePerItem);
        this.name = name;
        this.volume = volume;
        this.tolerance = computeTolerance(pricePerItem, volume);
    }

    /**
     * Resolves {@code name} to a product ID via {@link ProductInfo#fromDisplayName} and
     * constructs an instance. Returns empty when the name is unknown.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer)
     */
    public static Optional<OrderInfo> of(@NotNull String name, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume) {
        return ProductInfo.fromDisplayName(name).map(info -> new OrderInfo(name, info.getProductId(), side, pricePerItem, volume));
    }

    /**
     * Constructs directly from a known product ID, bypassing name resolution.
     *
     * @see #OrderInfo(String, String, TransactionType.Side, Double, Integer)
     */
    public static OrderInfo of(@NotNull String name, @NotNull String productId, @NotNull TransactionType.Side side, @NotNull Double pricePerItem, @NotNull Integer volume) {
        return new OrderInfo(name, productId, side, pricePerItem, volume);
    }

    public static Optional<OrderInfo> of(@NotNull Order order) {
        Optional<String> name = Optional.ofNullable(ResourceManager.getProductIdtoNameCache().get(order.productId()));

        if (name.isEmpty()) {
            PlayerLogger.sendError("Could not resolve name for " + order.describe() + " — try /bu updateresources or relaunch the game.", new Throwable());

            return Optional.empty();
        }

        return Optional.of(new OrderInfo(name.get(), order.productId(), order.side(), order.pricePerItem(), order.originalAmount()));
    }

    /**
     * Returns {@code true} when {@code other} is within the tolerance band for this order.
     *
     * <p>Tolerance = {@link #tolerance} + 1% of {@code other} to absorb floating-point residuals.
     */
    public boolean isPriceSimilarTo(double other) {
        return Util.genericIsSimilarValue(getPricePerItem(), other, tolerance + other * 0.01);
    }

    /**
     * Tests whether this order corresponds to {@code other}.
     *
     * @param strict when {@code true}: exact volume and pricePerUnit match required.
     *               when {@code false}: 5% volume tolerance and coin-rounding pricePerUnit tolerance.
     */
    public boolean isSimilarTo(OrderInfo other, boolean strict) {
        if (strict) return isStrictMatch(other);

        return isLooseMatch(other);
    }

    private boolean isStrictMatch(OrderInfo other) {
        return nullOrEqual(this.getName(), other.getName(), String::equalsIgnoreCase)
                && nullOrTrue(this.getPricePerItem(), other.getPricePerItem(), this::isPriceSimilarTo)
                && nullOrTrue(this.getVolume(), other.getVolume(), volume -> volume == other.getVolume())
                && nullOrTrue(this.getTransaction(), other.getTransaction(), transaction -> transaction.getSide() == other.getTransaction().getSide());
    }

    private boolean isLooseMatch(OrderInfo other) {
        return nullOrEqual(this.getName(), other.getName(), String::equalsIgnoreCase)
                && nullOrTrue(this.getPricePerItem(), other.getPricePerItem(), this::isPriceSimilarTo)
                && nullOrTrue(this.getVolume(), other.getVolume(), volume -> Util.genericIsSimilarValue(volume, other.getVolume(), 0.05 * other.getVolume()))
                && nullOrTrue(this.getTransaction(), other.getTransaction(), transaction -> transaction.getSide() == other.getTransaction().getSide());
    }

    /**
     * Finds the single best match for this order in {@code list}.
     *
     * <p>Tries strict matching first; falls back to loose. When multiple matches
     * survive, returns the one closest by volume then by pricePerUnit.
     *
     * @return best match, or {@link Optional#empty()} if none found
     */
    public Optional<OrderInfo> findOrderInList(List<OrderInfo> list) {
        List<OrderInfo> matches = findAllMatchesInList(list);

        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() == 1) return Optional.of(matches.getFirst());

        return Optional.of(bestMatch(matches));
    }

    /**
     * Returns all orders in {@code list} that resemble this order.
     * Strict matches are returned when any exist; otherwise loose matches are used.
     */
    public List<OrderInfo> findAllMatchesInList(List<OrderInfo> list) {
        List<OrderInfo> strict = list.stream().filter(order -> isSimilarTo(order, true)).toList();
        if (!strict.isEmpty()) return strict;

        List<OrderInfo> loose = new ArrayList<>();

        for (OrderInfo order : list) {
            if (isSimilarTo(order, false)) loose.add(order);
        }

        return loose;
    }

    /**
     * Selects the best match from a list of candidates by minimizing volume delta,
     * then pricePerUnit delta.
     */
    private OrderInfo bestMatch(List<OrderInfo> candidates) {
        Comparator<OrderInfo> byVolumeDelta = Comparator.comparingDouble(order -> Math.abs(order.getVolume() - this.getVolume()));

        Comparator<OrderInfo> byPriceDelta = Comparator.comparingDouble(order -> Math.abs(order.getPricePerItem() - this.getPricePerItem()));

        return candidates.stream()
                .min(byVolumeDelta.thenComparing(byPriceDelta))
                .orElse(candidates.getFirst());
    }

    /**
     * Computes the per-unit pricePerUnit tolerance for this order.
     *
     * <p>When the total order value is below {@link #FOLDING_THRESHOLD}, Hypixel does not
     * round, so tolerance is 0. Above the threshold, up to {@link #COIN_EPSILON} coins
     * of rounding can appear in the total, which translates to a per-unit tolerance of
     * {@code round(COIN_ROUNDING_CAP / volume, 1)}.
     */
    private static double computeTolerance(double price, int volume) {
        if (volume <= 0 || price <= 0) return COIN_EPSILON;
        if (price * volume < FOLDING_THRESHOLD) return 0.0;
        return Math.round((COIN_EPSILON / volume) * 10) / 10.0;
    }

    /**
     * Null-safe equality check using a custom comparator.
     */
    private static <T> boolean nullOrEqual(T a, T b, BiPredicate<T, T> predicate) {
        if (a == null || b == null) return true;

        return predicate.test(a, b);
    }

    /**
     * Null-safe predicate check; returns {@code true} when either value is null.
     */
    private static <T> boolean nullOrTrue(T a, T b, Predicate<T> predicate) {
        if (a == null || b == null) return true;

        return predicate.test(a);
    }

}
