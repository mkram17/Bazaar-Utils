package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;
import com.github.mkram17.bazaarutils.ui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.sign.SignManager;
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

public class BookmarkSearchWidget {
    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        List<ItemSlotButtonWidget> widgets = new ArrayList<>();

        boolean isTargetScreen = ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE);

        if (!(MinecraftClient.getInstance().currentScreen instanceof AccessorHandledScreen screen) || !isTargetScreen) {
            return Collections.emptyList();
        }

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, ContainerManager.getContainerName());

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.x() + dimensions.backgroundWidth() + spacing;
        int currentButtonY = dimensions.y() + spacing;

        List<Bookmark> bookmarks = BookmarkUtil.getBookmarks();

        for (Bookmark bookmark : bookmarks) {
            ItemStack configuredItem = bookmark.getItemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultStack() : configuredItem;
            MutableText text = Text.literal(bookmark.getName()).formatted(Formatting.BOLD);

            MarketPrices marketPrices = new MarketPrices(bookmark.getProductID());

            Style style = Style.EMPTY.withColor(Formatting.GRAY).withBold(false);
            text.append(Text.literal("\nBuy: " + Util.getPrettyString(marketPrices.getPriceForPosition(PricingPosition.MATCHED, OrderType.SELL)) + " coins").setStyle(style));
            text.append(Text.literal("\nSell: " + Util.getPrettyString(marketPrices.getPriceForPosition(PricingPosition.MATCHED, OrderType.BUY)) + " coins").setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    BookmarkUtil.SLOT_BUTTON_TEXTURES,
                    (btn) -> {
                        if (MinecraftClient.getInstance().isShiftPressed()) {
                            PlayerActionUtil.notifyAll("Removed " + bookmark.getName() + " bookmark from shift-click. Open Bazaar again to display changes.");
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

        boolean userHasSkyblockerBazaarOverlay = BUCompatibilityHelper.isSkyblockerLoaded() && BUCompatibilityHelper.isSkyblockerBazaarOverlayEnabled();

        if (userHasSkyblockerBazaarOverlay) {
            BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(false);
        }

        ContainerManager.clickSlot(BookmarkUtil.SIGN_SLOT_NUMBER, 0);
        SignManager.runOnNextSignOpen(event -> SignManager.setSignText(bookmark.getName(), true));

        if (userHasSkyblockerBazaarOverlay) {
            Util.tickExecuteLater(10, () -> BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(true));
        }
    }
}
