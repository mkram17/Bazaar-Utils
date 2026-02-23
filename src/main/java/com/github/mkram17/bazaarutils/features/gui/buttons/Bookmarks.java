package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.data.BookmarksStorage;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.mixin.AccessorHandledScreen;
import com.github.mkram17.bazaarutils.ui.CustomItemButton;
import com.github.mkram17.bazaarutils.ui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

@Slf4j
@Module
public class Bookmarks extends BUListener implements CustomItemButton {
    public record Bookmark(
            String name,
            ItemStack itemStack,
            OrderInfo orderInfo
    ) {
        public Bookmark {
            if (orderInfo == null && name != null) {
                orderInfo = new OrderInfo(name, OrderType.SELL, null, null, null, null);
            }
        }
    }

    private static final int SIGN_SLOT_NUMBER = 45;

    private static final Identifier DEFAULT = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/bookmark_widget_base");
    private static final Identifier HOVER = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/bookmark_widget_hover");

    public static final ButtonTextures SLOT_BUTTON_TEXTURES = new ButtonTextures(
            DEFAULT,
            HOVER
    );

    @Getter
    private transient Optional<Bookmark> current = Optional.empty();

    public static void saveBookmarks() {
        BookmarksStorage.INSTANCE.save();
    }

    public static List<Bookmark> bookmarks() {
        return BookmarksStorage.INSTANCE.get();
    }

    public static Optional<Bookmark> findMatchingBookmark(String itemName) {
        return bookmarks().stream()
                .filter(data -> data.name.equals(itemName))
                .findAny();
    }

    public static boolean isRegistryEnabled() {
        return ButtonsConfig.BookmarksConfig.TOGGLE_BOOKMARK_BUTTON.enabled;
    }

    public static boolean isButtonEnabled() {
        return ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.enabled;
    }

    @Override
    public int getSlotNumber() {
        return ButtonsConfig.BookmarksConfig.TOGGLE_BOOKMARK_BUTTON.slotNumber;
    }

    @Getter
    private transient ItemStack replacementItem;


    public Bookmarks() {
        super();
    }

    private boolean inCorrectScreen() {
        return ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.INDIVIDUAL_ITEM);
    }

    @EventHandler
    private void onReplaceItemEvent(ReplaceItemEvent event) {
        if (!isRegistryEnabled() || !shouldReplaceItem(event) || !inCorrectScreen()) {
            return;
        }

        String currentItemName = findItemNameFromContainer();

        current = findMatchingBookmark(currentItemName);
        buildReplacementItem();

        event.setReplacement(replacementItem);
    }

    @EventHandler
    private void onClick(SlotClickEvent event) {
        if (!isRegistryEnabled() || !wasButtonSlotClicked(event) || !inCorrectScreen()) {
            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        toggleBookmark();
    }

    private void buildReplacementItem() {
        boolean bookmarked = current.isPresent();

        this.replacementItem = new ItemStack(
                bookmarked ? Items.RED_STAINED_GLASS_PANE : Items.GREEN_STAINED_GLASS_PANE
        );

        replacementItem.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(bookmarked
                        ? "Remove " + current.get().name() + " Bookmarks"
                        : "Bookmarks " + findItemNameFromContainer())
        );

        replacementItem.set(
                BazaarUtils.CUSTOM_SIZE_COMPONENT,
                bookmarked ? "⃠ " : "★"
        );
    }

    private void toggleBookmark() {
        String name = findItemNameFromContainer();
        List<Bookmark> list = bookmarks();

        if (current.isPresent()) {
            list.remove(current.get());
            current = Optional.empty();
        } else {
            ItemStack actualItem = findItemStack(name);

            Bookmark newBookmark = new Bookmark(name, actualItem, null);
            list.add(newBookmark);

            current = Optional.of(newBookmark);
        }

        buildReplacementItem();

        saveBookmarks();
    }

    public static void onWidgetShiftClick(Bookmark bookmark) {
        bookmarks().remove(bookmark);
        saveBookmarks();
    }

    public static void onWidgetLeftClick(Bookmark bookmark) {
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        boolean userHasSkyblockerBazaarOverlay = BUCompatibilityHelper.isSkyblockerBazaarOverlayEnabled();

        if (userHasSkyblockerBazaarOverlay) {
            BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(false);
        }

        GUIUtils.clickSlot(SIGN_SLOT_NUMBER, 0);
        GUIUtils.runOnNextSignOpen(event -> GUIUtils.setSignText(bookmark.name, true));

        if (userHasSkyblockerBazaarOverlay) {
            Util.tickExecuteLater(10, () -> BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(true));
        }
    }

    @RegisterWidget
    public static List<ItemSlotButtonWidget> getWidgets() {
        if (!isButtonEnabled()) {
            return Collections.emptyList();
        }

        List<ItemSlotButtonWidget> widgets = new ArrayList<>();

        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
        boolean isTargetScreen = screenInfo.inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE);

        if (!(MinecraftClient.getInstance().currentScreen instanceof AccessorHandledScreen screen) || !isTargetScreen) {
            return Collections.emptyList();
        }

        ItemSlotButtonWidget.ScreenWidgetDimensions dimensions = ItemSlotButtonWidget.getSafeScreenDimensions(screen, screenInfo.getScreenName());

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.x() + dimensions.backgroundWidth() + spacing;
        int currentButtonY = dimensions.y() + spacing;

        List<Bookmark> bookmarks = bookmarks();

        for (Bookmark bookmark : bookmarks) {
            ItemStack configuredItem = bookmark.itemStack();

            final ItemStack itemForButton = (configuredItem == null) ? Items.BARRIER.getDefaultStack() : configuredItem;
            MutableText text = Text.literal(bookmark.name()).formatted(Formatting.BOLD);

            OrderInfo orderInfo = bookmark.orderInfo();
            orderInfo.updateMarketPrice();

            Style style = Style.EMPTY.withColor(Formatting.GRAY).withBold(false);
            text.append(Text.literal("\nBuy: " + Util.getPrettyString(orderInfo.getPriceForPosition(PricingPosition.MATCHED, OrderType.SELL)) + " coins").setStyle(style));
            text.append(Text.literal("\nSell: " + Util.getPrettyString(orderInfo.getPriceForPosition(PricingPosition.MATCHED, OrderType.BUY)) + " coins").setStyle(style));

            ItemSlotButtonWidget button = new ItemSlotButtonWidget(
                    buttonX,
                    currentButtonY,
                    buttonSize, buttonSize,
                    SLOT_BUTTON_TEXTURES,
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

    private static ItemStack findItemStack(String name) {
        ScreenHandler handler = GUIUtils.getHandledScreen();

        if (handler == null) {
            return null;
        }

        for (Slot slot : handler.slots) {
            ItemStack itemStack = slot.getStack();

            if (itemStack == null) {
                continue;
            }

            if (!itemStack.isEmpty() && itemStack.getName().getString().startsWith(name)) {
                return itemStack;
            }
        }

        for (Slot slot : handler.slots) {
            ItemStack itemStack = slot.getStack();

            if (!itemStack.isEmpty() && itemStack.getName().getString().contains(name)) {
                return itemStack;
            }
        }

        return Items.DIAMOND.getDefaultStack();
    }

    public static String findItemName(ChestLoadedEvent event) {
        String nameFromContainer = findItemNameFromContainer();

        if (!OrderInfo.isValidName(nameFromContainer) || nameFromContainer.length() >= 30) {
            return findItemNameFromItemStacks(event.getItemStacks(), nameFromContainer);
        }

        return nameFromContainer;
    }

    private static String findItemNameFromItemStacks(List<ItemStack> itemStacks, String nameFromContainer) {
        for (ItemStack stack : itemStacks) {
            if (stack == null) {
                continue;
            }

            if (!stack.isEmpty() && stack.getName().getString().startsWith(nameFromContainer)) {
                return stack.getCustomName().getString();
            }
        }

        return "???";
    }

    private static String findItemNameFromContainer() {
        ScreenInfo info = ScreenInfo.getCurrentScreenInfo();
        String name = info.getScreenName();

        return info.inMenu(ScreenInfo.BazaarMenuType.INSTA_BUY)
                ? name.substring(0, name.indexOf("➜") - 1)
                : name.substring(name.indexOf("➜") + 2);
    }
}
