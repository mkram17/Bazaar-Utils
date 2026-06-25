package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.data.stored.BookmarksStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import com.github.mkram17.bazaarutils.utils.minecraft.sound.AudioSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;

public class SearchBookmarkWidget {

    public static final Identifier DEFAULT_WIDGET_TEXTURE = Identifier.tryBuild(BazaarUtils.MOD_ID, "widget/bookmark_widget_base");
    public static final Identifier HOVER_WIDGET_TEXTURE = Identifier.tryBuild(BazaarUtils.MOD_ID, "widget/bookmark_widget_hover");

    public static final WidgetSprites SLOT_BUTTON_TEXTURES = new WidgetSprites(DEFAULT_WIDGET_TEXTURE, HOVER_WIDGET_TEXTURE);

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        var dimensions = WidgetManager.getScreenDimensions(BazaarScreenType.values());
        if (dimensions.isEmpty()) return Collections.emptyList();

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.get().x() + dimensions.get().backgroundWidth() + spacing;
        int currentButtonY = dimensions.get().y() + spacing;

        List<ItemSlotButtonWidget> widgets = new ArrayList<>();
        List<Bookmark> bookmarks = BookmarksStorage.INSTANCE.get();

        for (Bookmark bookmark : bookmarks) {
            ItemStack configuredItem = bookmark.itemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultInstance() : configuredItem;
            MutableComponent text = Component.literal(bookmark.name()).withStyle(ChatFormatting.BOLD);

            OptionalDouble instaBuy = PriceInfo.priceForPosition(bookmark.productId(),
                    TransactionType.of(TransactionType.Side.BUY, TransactionType.Method.INSTANT),
                    PricingPosition.MATCHED);
            OptionalDouble instaSell = PriceInfo.priceForPosition(bookmark.productId(),
                    TransactionType.of(TransactionType.Side.SELL, TransactionType.Method.INSTANT),
                    PricingPosition.MATCHED);

            Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false);

            text.append(Component.literal("\nInsta Buy: " + (instaBuy.isPresent() ? Util.getPrettyString(instaBuy.getAsDouble()) + " coins" : "N/A")).setStyle(style));
            text.append(Component.literal("\nInsta Sell: " + (instaSell.isPresent() ? Util.getPrettyString(instaSell.getAsDouble()) + " coins" : "N/A")).setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    SLOT_BUTTON_TEXTURES,
                    (btn) -> {
                        if (Minecraft.getInstance().hasShiftDown()) {
                            PlayerLogger.send("Removed " + bookmark.name() + " bookmark from shift-click. Open Bazaar again to display changes.");
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
        BookmarksStorage.remove(bookmark);
    }

    public static void onWidgetLeftClick(Bookmark bookmark) {
        SoundUtil.playSound(ItemButton.BUTTON_SOUND, 0.25f, 1.0f, AudioSource.UI);

        Optional<Integer> inventorySlot = SlotLookup.findScreenSlotByProductId(bookmark.productId());

        if (inventorySlot.isPresent()) {
            ContainerManager.clickSlot(inventorySlot.get(), 0);
            return;
        }

        PlayerLogger.runCommand("bz " + bookmark.name());
    }
}
