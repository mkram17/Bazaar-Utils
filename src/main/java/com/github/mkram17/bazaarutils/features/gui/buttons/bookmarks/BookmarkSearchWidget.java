package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BookmarkSearchWidget {
    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        var dimensions = WidgetManager.getScreenDimensions(BazaarScreenType.values());
        if (dimensions.isEmpty()) return Collections.emptyList();

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.get().x() + dimensions.get().backgroundWidth() + spacing;
        int currentButtonY = dimensions.get().y() + spacing;

        List<ItemSlotButtonWidget> widgets = new ArrayList<>();
        List<Bookmark> bookmarks = BookmarkUtil.getBookmarks();

        for (Bookmark bookmark : bookmarks) {
            ItemStack configuredItem = bookmark.itemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultInstance() : configuredItem;
            MutableComponent text = Component.literal(bookmark.name()).withStyle(ChatFormatting.BOLD);

            Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false);
            text.append(Component.literal("\nInsta Buy: " + Util.getPrettyString(OrderUtil.getPriceForPosition(bookmark.productID(), PricingPosition.MATCHED, TransactionType.INSTANT_BUY)) + " coins").setStyle(style));
            text.append(Component.literal("\nInsta Sell: " + Util.getPrettyString(OrderUtil.getPriceForPosition(bookmark.productID(), PricingPosition.MATCHED, TransactionType.INSTANT_SELL)) + " coins").setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    BookmarkUtil.SLOT_BUTTON_TEXTURES,
                    (btn) -> {
                        if (Minecraft.getInstance().hasShiftDown()) {
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

        Optional<Integer> inventorySlot = SlotLookup.findScreenSlotByProductId(bookmark.productID());

        if (inventorySlot.isPresent()) {
            ContainerManager.clickSlot(inventorySlot.get(), 0);
            return;
        }

        PlayerActionUtil.runCommand("bz " + bookmark.name());
    }
}
