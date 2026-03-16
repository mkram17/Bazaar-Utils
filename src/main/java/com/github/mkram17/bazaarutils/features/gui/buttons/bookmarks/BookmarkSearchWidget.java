package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.PlayerSlots;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BookmarkSearchWidget {
    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        var dimensions = WidgetManager.getScreenDimensions(BazaarScreens.ALL.toArray(ScreenType[]::new));
        if (dimensions.isEmpty()) return Collections.emptyList();

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.get().x() + dimensions.get().backgroundWidth() + spacing;
        int currentButtonY = dimensions.get().y() + spacing;

        List<ItemSlotButtonWidget> widgets = new ArrayList<>();
        List<Bookmark> bookmarks = BookmarkUtil.getBookmarks();

        for (Bookmark bookmark : bookmarks) {
            ItemStack configuredItem = bookmark.itemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultStack() : configuredItem;
            MutableText text = Text.literal(bookmark.name()).formatted(Formatting.BOLD);

            Style style = Style.EMPTY.withColor(Formatting.GRAY).withBold(false);
            text.append(Text.literal("\nBuy: " + Util.getPrettyString(OrderUtil.getPriceForPosition(bookmark.productId(), PricingPosition.MATCHED, OrderType.SELL)) + " coins").setStyle(style));
            text.append(Text.literal("\nSell: " + Util.getPrettyString(OrderUtil.getPriceForPosition(bookmark.productId(), PricingPosition.MATCHED, OrderType.BUY)) + " coins").setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    BookmarkUtil.SLOT_BUTTON_TEXTURES,
                    (btn) -> {
                        if (MinecraftClient.getInstance().isShiftPressed()) {
                            PlayerActionUtil.notifyAll("Removed " + bookmark.name() + " bookmark from shift-click. Open Bazaar again to display changes.");
                            onWidgetShiftClick(bookmark);
                        } else {
                            onWidgetLeftClick(bookmark);
                        }

                    },
                    itemForButton,
                    text
            );

            widgets.add(button);
            currentButtonY += buttonSize + spacing;
        }

        return widgets;
    }

    public static void onWidgetShiftClick(Bookmark bookmark) {
        BookmarkUtil.getBookmarks().remove(bookmark);
        BookmarkUtil.saveBookmarks();
    }

    public static void onWidgetLeftClick(Bookmark bookmark) {
        SoundUtil.playSound(ItemButton.BUTTON_SOUND, ItemButton.BUTTON_VOLUME);

        Optional<Integer> inventorySlot = PlayerSlots.findScreenSlotByProductId(bookmark.productId());

        if (inventorySlot.isPresent()) {
            ContainerManager.clickSlot(inventorySlot.get(), 0);
            return;
        }

        PlayerActionUtil.runCommand("bz " + bookmark.name());
    }
}
