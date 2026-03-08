package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.data.BookmarksStorage;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.sign.SignManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
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
public class Bookmarks extends BUListener implements ItemButton, BUToggleableFeature {
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
    public boolean isEnabled() {
        return isRegistryEnabled() || isButtonEnabled();
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
        return ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE);
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

        boolean userHasSkyblockerBazaarOverlay = BUCompatibilityHelper.isSkyblockerLoaded() && BUCompatibilityHelper.isSkyblockerBazaarOverlayEnabled();

        if (userHasSkyblockerBazaarOverlay) {
            BUCompatibilityHelper.setSkyblockerBazaarOverlayValue(false);
        }

        ContainerManager.clickSlot(SIGN_SLOT_NUMBER, 0);
        SignManager.runOnNextSignOpen(event -> SignManager.setSignText(bookmark.name(), true));

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

        var dimensions = WidgetManager.getScreenDimensions(BazaarScreens.ALL.toArray(ScreenType[]::new));
        if (dimensions.isEmpty()) return Collections.emptyList();

        int buttonSize = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.size;
        int spacing = ButtonsConfig.BookmarksConfig.OPEN_BOOKMARK_BUTTON.spacing;
        int buttonX = dimensions.get().x() + dimensions.get().backgroundWidth() + spacing;
        int currentButtonY = dimensions.get().y() + spacing;

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
        Optional<ScreenHandler> handler = ScreenManager.getCurrentScreenHandler(ScreenHandler.class);

        if (handler.isEmpty()) {
            return null;
        }

        for (Slot slot : handler.get().slots) {
            ItemStack itemStack = slot.getStack();

            if (itemStack == null) {
                continue;
            }

            if (!itemStack.isEmpty() && itemStack.getName().getString().startsWith(name)) {
                return itemStack;
            }
        }

        for (Slot slot : handler.get().slots) {
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
        String containerName = ContainerManager.getContainerName();

        if (ScreenManager.getInstance().isCurrent(BazaarScreens.INSTANT_BUY)) {
            return containerName.substring(0, containerName.indexOf("➜")-1);
        } else {
            return containerName.substring(containerName.indexOf("➜") + 2);
        }
    }
}
