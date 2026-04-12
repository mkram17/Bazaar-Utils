package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.SellTarget;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.google.common.collect.MapMaker;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stamps SellTarget onto player inventory ItemStacks based on what
 * the current bazaar screen can sell. Pure data concern.
 */
@Module
public class SellParser extends BUListener {
    public static final class InstantSell {
        @Getter
        @Nullable
        private static InstantSellParser.InstantSellResult result;

        public static boolean hasResult() {
            return result != null;
        }

        public static List<OrderInfo> orders() {
            return result != null ? result.items() : List.of();
        }

        public static Optional<InstantSellParser.InstantSellResult.OtherItems> otherItems() {
            return result != null ? result.otherItems() : Optional.empty();
        }

        public static void parse(ItemStack stack, ScreenContext context) {
            result = context.isAnyOf(BazaarScreens.ITEM_PAGE)
                    ? InstantSellParser.parseItemPageOrder(stack).orElse(new InstantSellParser.InstantSellResult(List.of(), Optional.empty()))
                    : InstantSellParser.parseOrders(stack);
        }

        public static void clear() {
            result = null;
        }
    }

    public static final class SellSacks {
        @Getter
        @Nullable
        private static SellSacksParser.SellSacksResult result;

        public static boolean hasResult() {
            return result != null;
        }

        public static List<OrderInfo> orders() {
            return result != null ? result.items() : List.of();
        }

        public static Optional<SellSacksParser.SellSacksResult.OtherItems> otherItems() {
            return result != null ? result.otherItems() : Optional.empty();
        }

        public static void parse(ItemStack stack) {
            result = SellSacksParser.parseOrders(stack);
        }

        public static void clear() {
            result = null;
        }
    }

    public static final class Targets {
        private static final Map<ItemStack, SellTarget> cache = new MapMaker()
                .weakKeys()
                .concurrencyLevel(1)
                .makeMap();

        static void stamp(ItemStack stack, SellTarget type) {
            cache.put(stack, type);
        }

        public static Optional<SellTarget> get(ItemStack stack) {
            return Optional.ofNullable(cache.get(stack));
        }

        public static void clearAll() {
            cache.clear();
        }

        public static void parse(ChestLoadedEvent event, List<OrderInfo> orders, SellTarget type) {
            if (orders.isEmpty()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            Set<String> names = orders.stream()
                    .map(OrderInfo::getName)
                    .collect(Collectors.toSet());

            for (Slot slot : event.getSlots()) {
                if (!slot.hasItem() || slot.container != client.player.getInventory()) continue;

                String name = DataTypeItemStackKt.getData(slot.getItem(), DataTypes.INSTANCE.getCLEAN_NAME());

                if (name != null && names.contains(name)) stamp(slot.getItem(), type);
            }
        }

        public static void parseOtherItems(ChestLoadedEvent event, int volumeBound, SellTarget type) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int remaining = volumeBound;

            for (Slot slot : event.getSlots()) {
                if (remaining <= 0) break;
                if (!slot.hasItem() || slot.container != client.player.getInventory()) continue;
                if (cache.containsKey(slot.getItem())) continue;

                String name = DataTypeItemStackKt.getData(slot.getItem(), DataTypes.INSTANCE.getCLEAN_NAME());
                if (name == null || !hasActiveBuyOrders(name)) continue;

                stamp(slot.getItem(), type);
                remaining -= slot.getItem().getCount();
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

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen({BazaarScreenType.MAIN_PAGE, BazaarScreenType.ITEMS_GROUP_PAGE, BazaarScreenType.ITEM_PAGE})
    private void onChestLoaded(ChestLoadedEvent event) {
        ScreenManager.getInstance().current().ifPresent(context -> {
            BazaarScreenHandler.getInstantSellItem(context).ifPresent(info -> {
                InstantSell.parse(info.itemStack(), context);
                Targets.parse(event, InstantSell.orders(), SellTarget.INSTANT_SELL);

                if (context.isAnyOf(BazaarScreens.MAIN_PAGE)) {
                    InstantSell.otherItems().ifPresent(other -> Targets.parseOtherItems(event, other.volume(), SellTarget.INSTANT_SELL));
                }
            });

            BazaarScreenHandler.getSellSacksItem(context).ifPresent(info -> {
                SellSacks.parse(info.itemStack());
            });
        });
    }

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen({BazaarScreenType.MAIN_PAGE, BazaarScreenType.ITEMS_GROUP_PAGE, BazaarScreenType.ITEM_PAGE})
    private void onInventoryChange(PlayerInventoryChangeEvent event) {
        if (InstantSell.orders().isEmpty() && SellSacks.orders().isEmpty()) return;

        ItemStack item = event.getItem();
        if (Targets.get(item).isPresent()) return;

        String name = DataTypeItemStackKt.getData(item, DataTypes.INSTANCE.getCLEAN_NAME());
        if (name == null) return;

        if (InstantSell.orders().stream().anyMatch(order -> order.getName().equalsIgnoreCase(name))) {
            Targets.stamp(item, SellTarget.INSTANT_SELL);
            return;
        }

        if (InstantSell.otherItems().isPresent() && Targets.hasActiveBuyOrders(name)) {
            Targets.stamp(item, SellTarget.INSTANT_SELL);
        }
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onContainerInitialized(ContainerInitializedEvent ignored) {
        if (InstantSell.hasResult() || SellSacks.hasResult()) return;
        clearAll();
    }

    @Subscription
    private void onContainerClose(ContainerCloseEvent ignored) {
        clearAll();
    }

    private void clearAll() {
        Targets.clearAll();
        InstantSell.clear();
        SellSacks.clear();
    }
}