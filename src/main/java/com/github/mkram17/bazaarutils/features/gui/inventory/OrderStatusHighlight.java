package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.*;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import com.google.common.collect.MapMaker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.IgnoreFiller;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.MustBeContainer;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.InventoryChangeEvent;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

import java.util.*;

@Module
public class OrderStatusHighlight extends BUListener implements ToggleableFeature {
    // TODO: REVIEW THIS
    // Hoisted as a member for the sake of readability (not to mangle the cache with the modifier/highlight api);
    // once the bazaar-data comes in, it is likely that this highlight can become as small as InstantSellHighlight is.
    @ItemModifier
    public static class Highlight implements LoreModifier, SlotHighlight {
        @Override
        public boolean isEnabled() {
            return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
        }

        @Override
        public HighlightStyle getHighlightStyle() {
            return InventoryConfig.ORDER_STATUS_HIGHLIGHT_STYLE;
        }

        private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.ORDERS_PAGE);

        @Override
        public ScreenMatcher<BazaarScreenType> screenConstraints() {
            return SCREENS; // to prevent instantiating the enumset every single iteration
        }

        public final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER);

        @Override
        public EnumSet<ModifierSource> getModifierSources() {
            return MODIFIER_SOURCES; // to prevent instantiating the LIST every single iteration
        }

        @Override
        public boolean appliesTo(ItemStack stack) {
            return cache.containsKey(stack);
        }

        @Override
        public Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot) {
            return get(stack).map(OrderStatusHighlight::getArgbFromPricingPosition);
        }

        @Override
        public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
            Optional<PricingPosition> position = get(stack);
            if (position.isEmpty()) return Result.UNMODIFIED;

            int slotIndex = findSlotIndex(stack);
            if (slotIndex == -1) return Result.UNMODIFIED;

            Order order = OrderUtil.getUserOrderFromIndex(slotIndex).orElse(null);
            if (order == null) return Result.UNMODIFIED;

            return withMerger(lore, merger -> {
                merger.copy(); // item name

                switch (position.get()) {
                    case COMPETITIVE -> merger.add(styledText("COMPETITIVE", InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR, true));
                    case MATCHED -> merger.add(styledText("MATCHED", InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR, true));
                    case OUTBID -> {
                        merger.add(styledText("OUTBID", InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, true));
                        merger.add(styledText("Market Price: " + Util.getPrettyString(order.getMarketPrice(order.getTransactionType().getSide())), InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, false));
                    }
                }

                if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                    merger.add(Component.literal("[BU] Buy: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.BUY)) + " coins"));
                    merger.add(Component.literal("[BU] Sell: " + Util.getPrettyString(order.getMarketPrice(TransactionType.Side.SELL)) + " coins"));
                }

                return Result.HANDLED;
            });
        }
    };

    private static final Map<ItemStack, PricingPosition> cache = new MapMaker()
            .weakKeys()
            .concurrencyLevel(1)
            .makeMap();

    public static Optional<PricingPosition> get(ItemStack stack) {
        return Optional.ofNullable(cache.get(stack));
    }

    private static void stamp(ItemStack stack, PricingPosition position) {
        cache.put(stack, position);
    }

    private static void clearAll() {
        cache.clear();
    }

    private static void resolve(ItemStack stack, int slotIndex) {
        Order order = OrderUtil.getUserOrderFromIndex(slotIndex)
                .filter(it -> it.getStatus() != null && it.getStatus() == OrderStatus.SET)
                .orElse(null);

        if (order == null) return;

        order.findPricingPosition().ifPresent(pos -> stamp(stack, pos));
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    public OrderStatusHighlight() {
        super();
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        for (Slot slot : event.getContainerSlots()) {
            if (slot.hasItem()) resolve(slot.getItem(), slot.getContainerSlot());
        }
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @MustBeContainer
    @OnlyBazaarScreen(BazaarScreenType.ORDERS_PAGE)
    @IgnoreFiller
    private void onInventoryChange(InventoryChangeEvent event) {
        resolve(event.getItem(), event.getSlot().getContainerSlot());
    }

    // Clear doesnt need to be gated to @OnlyBazaarScreen(any = true) because it's cheap and also
    // avoids the predicate's screen-resolution ordering hazard for ContainerInitializedEvent.
    @Subscription
    @OnlyOnSkyBlock
    private void onContainerInitialized(ContainerInitializedEvent ignored) {
        clearAll();
    }

    @Subscription
    @OnlyWhenEnabled
    private void onContainerClose(ContainerCloseEvent ignored) {
        clearAll();
    }

    private static int getArgbFromPricingPosition(PricingPosition position) {
        return switch (position) {
            case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
            case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
            case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
        };
    }

    private static int findSlotIndex(ItemStack stack) {
        AbstractContainerScreen<?> screen = ScreenManager.getScreen(AbstractContainerScreen.class).orElse(null);
        if (screen == null) return -1;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack item = slot.getItem();
            if (item == stack || VisualItemAccessorKt.getVisualItem(item) == stack) return slot.getContainerSlot();
        }

        return -1;
    }

    private static Component styledText(String content, int rgb, boolean bold) {
        return Component.literal(content).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));
    }
}