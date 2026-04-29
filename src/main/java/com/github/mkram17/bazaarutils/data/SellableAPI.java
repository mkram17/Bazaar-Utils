package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.InstantSellParser;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SellSacksParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.SellablePageLayout;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.google.common.collect.MapMaker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent;
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Maintains the current Bazaar screen's player-sellable data across three namespaces:
 *
 * <ul>
 *   <li>{@link InstantSell} — parsed Instant Sell orders for the context.</li>
 *   <li>{@link SellSacks} — parsed Sell Sacks orders for the context.</li>
 *   <li>{@link Targets} — the players held stacks which would be sold per an Instant Sell</li>
 * </ul>
 *
 * Do note the following:
 *
 * {@code SellSacks} is imprecise as players can hold very well over 1300 products on their sacks,
 * and thus for any sellable context (a scoped catalog page or the direct bazaar overview),
 * most items will be folded onto {@link SellSacksParser.SellSacksResult.OtherItems}.
 *
 * This is a limitation per the lack of a proper {@code BazaarScreenType} -> {@code CATALOGS} reference
 * to simply index via {@link SacksAPI}, because Hypixel doesn't offer a proper endpoint of the Bazaar Catalogs/Products.
 */
@Module
public class SellableAPI extends BUListener implements ScreenConstrained {
    private record SellDataState(
            @Nullable InstantSellParser.InstantSellResult instantSell,
            @Nullable SellSacksParser.SellSacksResult sellSacks,
            Map<ItemStack, TransactionType> targets
    ) {
        static SellDataState empty() {
            return new SellDataState(null, null, new MapMaker().weakKeys().concurrencyLevel(1).makeMap());
        }
    }

    private static final AtomicReference<SellDataState> STATE = new AtomicReference<>(SellDataState.empty());

    public static final class InstantSell {
        public static boolean hasResult() {
            return STATE.get().instantSell() != null;
        }

        public static List<OrderInfo> orders() {
            var cache = STATE.get().instantSell();

            return cache != null ? cache.items() : List.of();
        }

        public static Optional<InstantSellParser.InstantSellResult.OtherItems> otherItems() {
            var cache = STATE.get().instantSell();

            return cache != null ? cache.otherItems() : Optional.empty();
        }

        public static void parse(ItemStack stack, ScreenContext context) {
            var result = context.is(BazaarScreenType.PRODUCT_PAGE)
                    ? InstantSellParser.parseProductPageOrder(stack).orElse(new InstantSellParser.InstantSellResult(List.of(), Optional.empty()))
                    : InstantSellParser.parseInstantSellOrders(stack);

            STATE.updateAndGet(data -> new SellDataState(result, data.sellSacks(), data.targets()));
        }
    }

    public static final class SellSacks {
        public static boolean hasResult() {
            return STATE.get().sellSacks() != null;
        }

        public static List<OrderInfo> orders() {
            var cache = STATE.get().sellSacks();

            return cache != null ? cache.items() : List.of();
        }

        public static Optional<SellSacksParser.SellSacksResult.OtherItems> otherItems() {
            var cache = STATE.get().sellSacks();

            return cache != null ? cache.otherItems() : Optional.empty();
        }

        public static void parse(ItemStack stack) {
            var result = SellSacksParser.parseSackOrders(stack);

            STATE.updateAndGet(data -> new SellDataState(data.instantSell(), result, data.targets()));
        }
    }

    public static final class Targets {
        static void stamp(ItemStack stack, TransactionType type) {
            STATE.get().targets().put(stack, type);
        }

        public static Optional<TransactionType> get(ItemStack stack) {
            return Optional.ofNullable(STATE.get().targets().get(stack));
        }

        public static boolean containsKey(ItemStack stack) {
            return STATE.get().targets().containsKey(stack);
        }

        public static void parse(ContainerLoadedEvent event, List<OrderInfo> orders, TransactionType type) {
            if (orders.isEmpty()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            Set<String> names = orders.stream()
                    .map(OrderInfo::getName)
                    .collect(Collectors.toSet());

            for (Slot slot : event.getPlayerSlots()) {
                var item = slot.getItem();

                if (slot.container != client.player.getInventory()) continue;

                String name = DataTypeItemStackKt.getData(item, DataTypes.INSTANCE.getCLEAN_NAME());

                if (name != null && names.contains(name)) {
                    stamp(item, type);
                }
            }
        }

        public static void parseOtherItems(ContainerLoadedEvent event, int volumeBound, TransactionType type) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int remaining = volumeBound;

            for (Slot slot : event.getPlayerSlots()) {
                var item = slot.getItem();

                if (remaining <= 0) break;
                if (slot.container != client.player.getInventory()) continue;
                if (Targets.containsKey(item)) continue;

                String name = DataTypeItemStackKt.getData(item, DataTypes.INSTANCE.getCLEAN_NAME());
                if (name == null || !hasActiveBuyOrders(name)) continue;

                stamp(item, type);
                remaining -= item.getCount();
            }
        }

        private static boolean hasActiveBuyOrders(String name) {
            Optional<String> productId = BazaarDataUtil.findProductIdOptional(name);
            if (productId.isEmpty()) return false;

            OptionalDouble topPrice = BazaarDataUtil.findItemPriceOptional(productId.get(), TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER));
            if (topPrice.isEmpty() || topPrice.getAsDouble() == 0.0) return false;

            OptionalInt orderCount = BazaarDataUtil.getOrderCountOptional(productId.get(), TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.ORDER), topPrice.getAsDouble());
            return orderCount.isPresent() && orderCount.getAsInt() > 0;
        }
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstrainsInterface = true)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        var context = event.asContext();

        SellablePageLayout.getInstantSellItem(context).ifPresent(info -> {
            InstantSell.parse(info.itemStack(), context);
            Targets.parse(event, InstantSell.orders(), TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT));

            if (context.is(BazaarScreenType.MAIN_PAGE) || context.is(BazaarScreenType.SEARCH_PAGE)) {
                InstantSell.otherItems().ifPresent(other -> Targets.parseOtherItems(event, other.volume(), TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT)));
            }
        });

        SellablePageLayout.getSellSacksItem(context).ifPresent(info -> {
            SellSacks.parse(info.itemStack());
        });
    }

    @Subscription(priority = Priority.HIGH)
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstrainsInterface = true)
    private void onInventoryChange(PlayerInventoryChangeEvent event) {
        if (InstantSell.orders().isEmpty() && SellSacks.orders().isEmpty()) return;

        ItemStack item = event.getItem();
        if (Targets.get(item).isPresent()) return;

        String name = DataTypeItemStackKt.getData(item, DataTypes.INSTANCE.getCLEAN_NAME());
        if (name == null) return;

        if (InstantSell.orders().stream().anyMatch(order -> order.getName().equalsIgnoreCase(name))) {
            Targets.stamp(item, TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT));
            return;
        }

        if (InstantSell.otherItems().isPresent() && Targets.hasActiveBuyOrders(name)) {
            Targets.stamp(item, TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT));
        }
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onScreenChange(ScreenChangeEvent ignored) {
        clearAll();
    }

    private void clearAll() {
        STATE.set(SellDataState.empty());
    }
}