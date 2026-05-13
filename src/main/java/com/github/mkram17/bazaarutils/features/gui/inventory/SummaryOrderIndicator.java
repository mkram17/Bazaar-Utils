package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.HandledOrderAPI;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ProductPageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@ItemModifier
public class SummaryOrderIndicator implements LoreModifier {
    @Override
    public boolean isEnabled() {
        return InventoryConfig.SUMMARY_ORDER_INDICATOR_TOGGLE;
    }

    @Override
    public ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.AT_MODIFICATION;
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.PRODUCT_PAGE, BazaarScreenType.BUY_ORDER_PRICE, BazaarScreenType.SELL_OFFER_PRICE, BazaarScreenType.COMPLETED_BUY_ORDER_OPTIONS);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    private final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER);

    @Override
    public EnumSet<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES;
    }

    public SummaryOrderIndicator() {}

    @Override
    public boolean appliesTo(ItemStack stack) {
        return sideFor(stack, null).isPresent();
    }

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return sideFor(stack, context).isPresent();
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        return sideFor(stack, context)
                .flatMap(side -> resolveProductId(side, context))
                .map(ctx -> injectMarkers(lore, ctx))
                .orElse(Result.UNMODIFIED);
    }

    private static Optional<TransactionType.Side> sideFor(ItemStack stack, @Nullable ScreenContext context) {
        var menuOpt = context != null
                ? context.as(AbstractContainerScreen.class).map(screen -> (ChestMenu) screen.getMenu())
                : ScreenManager.getMenu(ChestMenu.class);

        return menuOpt.flatMap(menu -> sideFor(menu, stack));
    }

    private static Optional<TransactionType.Side> sideFor(ChestMenu menu, ItemStack stack) {
        var container = menu.getContainer();

        var createBuyOrder = BazaarSlots.PRODUCT_PAGE.CREATE_BUY_ORDER.query(container).first();
        if (createBuyOrder.isPresent()) {
            ItemStack item = createBuyOrder.get().itemStack();

            if (item == stack)
                return Optional.of(TransactionType.Side.BUY);
        }

        var createSellOffer = BazaarSlots.PRODUCT_PAGE.CREATE_SELL_OFFER.query(container).first();
        if (createSellOffer.isPresent()) {
            ItemStack item = createSellOffer.get().itemStack();

            if (item == stack) {
                return Optional.of(TransactionType.Side.SELL);
            }
        }

        var buyCustomPrice = BazaarSlots.BUY_ORDER.INPUT_CUSTOM_PRICE.query(container).first();
        if (buyCustomPrice.isPresent()) {
            ItemStack item = buyCustomPrice.get().itemStack();

            if (item == stack) {
                return Optional.of(TransactionType.Side.BUY);
            }
        }

        var flipOrder = BazaarSlots.ORDER_OPTIONS.FLIP_FILLED_BUY_ORDER.query(container).first();
        if (flipOrder.isPresent()) {
            ItemStack item = flipOrder.get().itemStack();

            if (item == stack) {
                return Optional.of(TransactionType.Side.SELL);
            }
        }

        var sellCustomPrice = BazaarSlots.SELL_OFFER.INPUT_CUSTOM_PRICE.query(container).first();
        if (sellCustomPrice.isPresent()) {
            ItemStack item = sellCustomPrice.get().itemStack();

            if (item == stack) {
                return Optional.of(TransactionType.Side.SELL);
            }
        }


        return Optional.empty();
    }

    private static Optional<InjectionContext> resolveProductId(TransactionType.Side side, @Nullable ScreenContext context) {
        var manager = ScreenManager.getInstance();

        Optional<ProductInfo> info = Optional.ofNullable(context)
                .flatMap(ProductPageLayout::getDisplayProductInfo)
                .or(() -> manager.findBack(BazaarScreenType.PRODUCT_PAGE)
                        .flatMap(ProductPageLayout::getDisplayProductInfo));

        if (info.isEmpty()) {
            info = HandledOrderAPI.getForOptions()
                    .flatMap(order -> ProductInfo.fromProductId(order.productId()));
        }

        return info.map(i -> new InjectionContext(i, side));
    }

    /**
     * Caches the parsed price-level double for a given lore {@link Component}.
     * {@link #parsePriceLevel} allocates strings and calls
     * {@link Double#parseDouble} for every line on every frame; the result is
     * purely a function of the Component's content and never changes.
     */
    private final Cache<Component, Optional<Double>> priceLevelCache = CacheBuilder.newBuilder().weakKeys().build();

    private Result injectMarkers(List<Component> lore, InjectionContext ctx) {
        var storage = UserOrdersStorage.orders();
        if (storage.isEmpty()) return Result.UNMODIFIED;

        List<Order> userOrders = storage.stream()
                .filter(Order::isActive)
                .filter(Order.forProduct(ctx.productInfo().getProductId(), ctx.side()))
                .toList();

        if (userOrders.isEmpty()) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            boolean mutated = false;

            while (merger.canRead()) {
                Component line = merger.peek();
                Double price = parsePriceLevel(line);

                if (price == null) {
                    merger.copy();
                    continue;
                }

                merger.copy();

                List<Order> atPrice = userOrders.stream()
                        .filter(order -> order.pricePerItem() == price)
                        .toList();

                if (!atPrice.isEmpty()) {
                    merger.add(markerLine(atPrice));
                    mutated = true;
                }
            }

            return mutated ? Result.HANDLED : Result.UNMODIFIED;
        });
    }

    @Nullable
    private Double parsePriceLevel(Component line) {
        try {
            return priceLevelCache.get(line, () -> {
                var siblings = line.getSiblings();
                if (siblings.size() != 8) return Optional.empty();

                try {
                    return Optional.of(Double.parseDouble(
                            siblings.get(1).getString()
                                    .replace(" coins ", "")
                                    .replace(",", "")
                                    .trim()
                    ));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }).orElse(null);
        } catch (ExecutionException e) {
            return null;
        }
    }

    private Component markerLine(List<Order> orders) {
        int totalUnfilled = orders.stream().mapToInt(Order::unfilledAmount).sum();
        int orderCount = orders.size();
        String noun = orderCount == 1 ? "order" : "orders";

        return Component.literal("  - ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(withAtModificationIndicator(Component.literal("Your order: ").withStyle(ChatFormatting.GRAY)))
                .append(Component.literal(NumberFormat.getNumberInstance(Locale.US).format(totalUnfilled) + "x").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" in ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(orderCount + " " + noun).withStyle(ChatFormatting.WHITE));
    }

    private record InjectionContext(ProductInfo productInfo, TransactionType.Side side) {}
}