package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

import java.util.*;

@ItemModifier
public class OrderStatusHighlight implements AbstractItemModifier, LoreModifier, ToggleableFeature {

    private sealed interface HighlightState permits HighlightState.Position, HighlightState.FilledAwaitingClaim {
        record Position(PricingPosition position) implements HighlightState {}
        record FilledAwaitingClaim() implements HighlightState {}
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return context.map(it -> it.equals(BazaarScreenType.ORDERS_PAGE)).orElse(false);
    }

    public OrderStatusHighlight() {}

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot) {
        return slot != null && resolveHighlight(slot.getContainerSlot()).isPresent();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        return resolveHighlight(findSlotIndex(stack)).isPresent();
    }

    @Override
    public List<ModifierSource> getModifierSources() {
        return List.of(ModifierSource.INVENTORY);
    }

    @Override
    public Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot) {
        if (slot == null) return Optional.empty();
        return resolveHighlight(slot.getContainerSlot()).map(OrderStatusHighlight::colorFor);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous) {
        int slotIndex = findSlotIndex(stack);
        if (slotIndex == -1) return Result.UNMODIFIED;

        var order = UserOrdersStorage.getOrderFromSlotIndex(slotIndex).orElse(null);
        if (order == null) return Result.UNMODIFIED;

        var transaction = TransactionType.of(order.side(), TransactionType.Method.ORDER);

        var highlight = resolveHighlight(order).orElse(null);
        if (highlight == null) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            merger.copy();

            switch (highlight) {
                case HighlightState.FilledAwaitingClaim ignored -> merger.add(styledText("FILLED", InventoryConfig.ORDER_STATUS_HIGHLIGHT_FILLED_COLOR, true));
                case HighlightState.Position position -> {
                    switch (position.position()) {
                        case COMPETITIVE -> merger.add(styledText("COMPETITIVE", InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR, true));
                        case MATCHED -> merger.add(styledText("MATCHED", InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR, true));
                        case OUTBID -> {
                            merger.add(styledText("OUTBID", InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, true));
                            PriceInfo.marketPrice(order.productId(), transaction).ifPresent(price -> merger.add(styledText("Market Price: " + Util.getPrettyString(price), InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, false)));
                        }
                    }
                }
            }

            if (DeveloperConfig.DEVELOPER_MODE_TOGGLE) {
                PriceInfo.marketPrice(order.productId(), TransactionType.of(TransactionType.Side.BUY,  TransactionType.Method.ORDER))
                        .ifPresent(price -> merger.add(Component.literal("[BU] Buy: "  + Util.getPrettyString(price) + " coins")));
                PriceInfo.marketPrice(order.productId(), TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.ORDER))
                        .ifPresent(price -> merger.add(Component.literal("[BU] Sell: " + Util.getPrettyString(price) + " coins")));
            }

            return Result.MODIFIED;
        });
    }

    private static Optional<HighlightState> resolveHighlight(int slotIndex) {
        return UserOrdersStorage.getOrderFromSlotIndex(slotIndex).flatMap(OrderStatusHighlight::resolveHighlight);
    }

    private static Optional<HighlightState> resolveHighlight(Order order) {
        var storage = UserOrdersStorage.INSTANCE.get();
        List<Order> userOrders = storage != null ? storage : List.of();

        return switch (order.status()) {
            case OrderStatus.Filled ignored -> Optional.of(new HighlightState.FilledAwaitingClaim());
            case OrderStatus.Set ignored -> order.position(userOrders).map(HighlightState.Position::new);
            case OrderStatus.Partial ignored -> order.position(userOrders).map(HighlightState.Position::new);
            default -> Optional.empty();
        };
    }

    private static int findSlotIndex(ItemStack stack) {
        return ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class)
                .map(screen -> {
                    for (Slot slot : screen.getMenu().slots) {
                        ItemStack item = slot.getItem();

                        if (item == stack || VisualItemAccessorKt.getVisualItem(item) == stack) {
                            return slot.getContainerSlot();
                        }
                    }
                    return -1;
                })
                .orElse(-1);
    }

    private static int colorFor(HighlightState state) {
        return switch (state) {
            case HighlightState.Position position -> switch (position.position()) {
                case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
                case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
                case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
            };
            case HighlightState.FilledAwaitingClaim ignored -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_FILLED_COLOR;
        };
    }

    private static Component styledText(String content, int rgb, boolean bold) {
        return Component.literal(content)
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb))
                        .withBold(bold));
    }
}