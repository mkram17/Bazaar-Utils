package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.RenderedOrdersIndex;
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
    private sealed interface HighlightState permits
            HighlightState.Unsettled,
            HighlightState.Settled {
        static Optional<HighlightState> create(Order order) {
            return switch (order.status()) {
                case OrderStatus.Set _, OrderStatus.Partial _ -> order.position(RenderedOrdersIndex.orders(), InventoryConfig.ORDER_STATUS_SELF_OUTBID_TOGGLE).map(Unsettled::new);
                case OrderStatus.Filled _, OrderStatus.Expired _ -> Optional.of(new Settled((OrderStatus.Settled) order.status()));
                case OrderStatus.Cancelled _, OrderStatus.Claimed _ -> Optional.empty();
            };
        }

        /** The slot's highlight color, in whichever channel this state targets. */
        int color();

        /** The label this state contributes to the item's lore. */
        Component label();

        /** Mirrors {@link OrderStatus.BookState}: still resting in the live book. */
        record Unsettled(PricingPosition position) implements HighlightState {
            @Override
            public int color() {
                return switch (position) {
                    case COMPETITIVE -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_COMPETITIVE_COLOR;
                    case MATCHED -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_MATCHED_COLOR;
                    case OUTBID -> InventoryConfig.ORDER_STATUS_HIGHLIGHT_OUTBID_COLOR;
                };
            }

            @Override
            public Component label() {
                return switch (position) {
                    case COMPETITIVE -> styledText("COMPETITIVE", color(), true);
                    case MATCHED -> styledText("MATCHED", color(), true);
                    case OUTBID -> styledText("OUTBID", color(), true);
                };
            }
        }

        /** Mirrors {@link OrderStatus.Settled}: left the live book, still actionable. */
        record Settled(OrderStatus.Settled status) implements HighlightState {
            @Override
            public int color() {
                return InventoryConfig.ORDER_STATUS_HIGHLIGHT_SETTLED_COLOR;
            }

            @Override
            public Component label() {
                return switch (status) {
                    case OrderStatus.Filled _ -> styledText("FILLED", color(), true);
                    case OrderStatus.Expired _ -> styledText("EXPIRED", color(), true);
                };
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_TOGGLE;
    }

    @Override
    public HighlightStyle getHighlightStyle() {
        return InventoryConfig.ORDER_STATUS_HIGHLIGHT_STYLE;
    }

    public final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.ORDERS_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
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

        return resolveHighlight(slot.getContainerSlot()).map(HighlightState::color);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        int slotIndex = findSlotIndex(stack, context);
        if (slotIndex == -1) return Result.UNMODIFIED;

        var order = RenderedOrdersIndex.get(slotIndex).orElse(null);
        if (order == null) return Result.UNMODIFIED;

        var highlight = HighlightState.create(order).orElse(null);
        if (highlight == null) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            merger.copy();

            merger.add(highlight.label());

            if (highlight instanceof HighlightState.Unsettled(var position) && position == PricingPosition.OUTBID) {
                var transaction = TransactionType.of(order.side(), TransactionType.Method.ORDER);

                PriceInfo.marketPrice(order.productId(), transaction)
                        .ifPresent(price -> merger.add(styledText(
                                "Market Price: " + Util.getPrettyString(price),
                                highlight.color(), false)));
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
        return RenderedOrdersIndex.get(slotIndex).flatMap(HighlightState::create);
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

    private static Component styledText(String content, int rgb, boolean bold) {
        return Component.literal(content)
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb))
                        .withBold(bold)
                        .withItalic(false));
    }
}