package com.github.mkram17.bazaarutils.features.gui.inventory.highlights;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.RenderedOrdersIndex;
import com.github.mkram17.bazaarutils.data.stored.UserOrdersStorage;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderStatus;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

import java.util.*;

@ItemModifier
public class OrderStatusHighlight implements LoreModifier, SlotHighlight {

    private sealed interface HighlightState permits HighlightState.Position, HighlightState.FilledAwaitingClaim {
        record Position(PricingPosition position) implements HighlightState {}
        record FilledAwaitingClaim() implements HighlightState {}
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    @Override
    public HighlightStyle getHighlightStyle() {
        return InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_STYLE;
    }

    public final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.ORDERS_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER);

    @Override
    public EnumSet<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES;
    }

    public OrderStatusHighlight() {}

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return slot != null && resolveHighlight(slot.getContainerSlot()).isPresent();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        return resolveHighlight(findSlotIndex(stack, null)).isPresent();
    }

    @Override
    public Optional<Integer> highlightColor(ItemStack stack, @Nullable Slot slot) {
        if (slot == null) return Optional.empty();

        return resolveHighlight(slot.getContainerSlot()).map(OrderStatusHighlight::colorFor);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        int slotIndex = findSlotIndex(stack, context);
        if (slotIndex == -1) return Result.UNMODIFIED;

        var order = RenderedOrdersIndex.get(slotIndex).orElse(null);
        if (order == null) return Result.UNMODIFIED;

        var transaction = TransactionType.of(order.side(), TransactionType.Method.ORDER);

        var highlight = resolveHighlight(order).orElse(null);
        if (highlight == null) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            merger.copy();

            switch (highlight) {
                case HighlightState.FilledAwaitingClaim ignored -> merger.add(styledText("FILLED", InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_FILLED_COLOR, true));
                case HighlightState.Position position -> {
                    switch (position.position()) {
                        case COMPETITIVE -> merger.add(styledText("COMPETITIVE", InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR, true));
                        case MATCHED -> merger.add(styledText("MATCHED", InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR, true));
                        case OUTBID -> {
                            merger.add(styledText("OUTBID", InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, true));
                            PriceInfo.marketPrice(order.productId(), transaction).ifPresent(price -> merger.add(styledText("Market Price: " + Util.getPrettyString(price), InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR, false)));
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

            return Result.HANDLED;
        });
    }

    private static Optional<HighlightState> resolveHighlight(int slotIndex) {
        return RenderedOrdersIndex.get(slotIndex).flatMap(OrderStatusHighlight::resolveHighlight);
    }

    private static Optional<HighlightState> resolveHighlight(Order order) {
        var storage = UserOrdersStorage.orders();

        return switch (order.status()) {
            case OrderStatus.Filled ignored -> Optional.of(new HighlightState.FilledAwaitingClaim());
            case OrderStatus.Set ignored -> order.position(storage, InventoryConfig.Highlights.ORDER_STATUS_SELF_OUTBID_TOGGLE).map(HighlightState.Position::new);
            case OrderStatus.Partial ignored -> order.position(storage, InventoryConfig.Highlights.ORDER_STATUS_SELF_OUTBID_TOGGLE).map(HighlightState.Position::new);
            default -> Optional.empty();
        };
    }

    private static int findSlotIndex(ItemStack stack, @Nullable ScreenContext context) {
        var menuOpt = context != null
                ? context.as(AbstractContainerScreen.class).map(AbstractContainerScreen::getMenu)
                : ScreenManager.getMenu(AbstractContainerMenu.class);

        return menuOpt.map(menu -> {
            for (Slot slot : menu.slots) {
                ItemStack item = slot.getItem();

                if (item == stack || VisualItemAccessorKt.getVisualItem(item) == stack) {
                    return slot.getContainerSlot();
                }
            }

            return -1;
        }).orElse(-1);
    }

    private static int colorFor(HighlightState state) {
        return switch (state) {
            case HighlightState.Position position -> switch (position.position()) {
                case COMPETITIVE -> InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
                case MATCHED -> InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
                case OUTBID -> InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
            };
            case HighlightState.FilledAwaitingClaim ignored -> InventoryConfig.Highlights.ORDER_STATUS_HIGHLIGHT_FILLED_COLOR;
        };
    }

    private static Component styledText(String content, int rgb, boolean bold) {
        return Component.literal(content)
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb))
                        .withBold(bold)
                        .withItalic(false));
    }
}