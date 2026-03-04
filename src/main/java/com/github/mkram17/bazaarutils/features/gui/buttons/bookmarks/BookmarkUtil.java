package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.BookmarksStorage;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import lombok.Getter;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class BookmarkUtil {

    @Getter
    public static Optional<Bookmark> currentBookmarkOpt = Optional.empty();
    public static final int SIGN_SLOT_NUMBER = 45;
    public static final Identifier DEFAULT_WIDGET_TEXTURE = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/bookmark_widget_base");
    public static final Identifier HOVER_WIDGET_TEXTURE = Identifier.tryParse(BazaarUtils.MOD_ID, "widget/bookmark_widget_hover");
    public static final ButtonTextures SLOT_BUTTON_TEXTURES = new ButtonTextures(DEFAULT_WIDGET_TEXTURE, HOVER_WIDGET_TEXTURE);

    public static ItemStack findItemStack(String name) {
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

    public static String findItemName(List<ItemStack> containerItems) {
        String nameFromContainer = findItemNameFromContainer();

        if (!OrderInfo.isValidName(nameFromContainer) || nameFromContainer.length() >= 30) {
            return findItemNameFromItemStacks(containerItems, nameFromContainer);
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

    public static String findItemNameFromContainer() {
        String containerName = ContainerManager.getContainerName();

        if (ScreenManager.getInstance().isCurrent(BazaarScreens.INSTANT_BUY)) {
            return containerName.substring(0, containerName.indexOf("➜")-1);
        } else {
            return containerName.substring(containerName.indexOf("➜") + 2);
        }
    }

    public static void saveBookmarks() {
        BookmarksStorage.INSTANCE.save();
    }

    public static boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.ALL.toArray(ScreenType[]::new));
    }

    public static List<Bookmark> getBookmarks() {
        return BookmarksStorage.INSTANCE.get();
    }

    public static Optional<Bookmark> findMatchingBookmark(String itemName) {
        return BookmarkUtil.getBookmarks().stream()
                .filter(data -> data.name().equals(itemName))
                .findAny();
    }
}
