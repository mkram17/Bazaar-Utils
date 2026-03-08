package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.ItemGroups;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

@Module
public class ToggleBookmarkButton extends BUListener implements ItemButton {
    @Override
    public int getSlotIndex() {
        return 0;
    }

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(BookmarkUtil.currentBookmarkOpt::isEmpty, ItemGroups.BOOKMARKED_STATE_GROUP);
    }

    private boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE);
    }

    public ToggleBookmarkButton() {}

    private Optional<String> resolveCurrentItemName() {
        return ScreenManager.getInstance()
                .current()
                .flatMap(BazaarScreenHandler::getDisplayItemName);
    }

    @Override
    public ItemStack getReplacementItem(int size) {
        boolean bookmarked = BookmarkUtil.currentBookmarkOpt.isPresent();

        ItemStack stack = ItemButton.super.getReplacementItem(size);

        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(bookmarked
                        ? "Remove " + BookmarkUtil.currentBookmarkOpt.get().name() + " Bookmark"
                        : "Bookmark " + resolveCurrentItemName().orElse("?")));

        stack.set(CustomDataComponents.CUSTOM_SIZE, bookmarked ? "⃠ " : "★");

        return stack;
    }

    @EventHandler
    private void onReplaceItemEvent(ReplaceItemEvent event) {
        if (!shouldReplaceItem(event) || !inCorrectScreen()) return;

        resolveCurrentItemName().ifPresent(name -> BookmarkUtil.currentBookmarkOpt = BookmarkUtil.findMatchingBookmark(name));

        event.setReplacement(getReplacementItem());
    }

    @EventHandler
    private void onClick(SlotClickEvent event) {
        if (!wasButtonClicked(event) || !inCorrectScreen()) return;

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        resolveCurrentItemName().ifPresent(this::toggleBookmark);
    }

    private void toggleBookmark(String name) {
        List<Bookmark> list = BookmarkUtil.getBookmarks();

        if (BookmarkUtil.currentBookmarkOpt.isPresent()) {
            list.remove(BookmarkUtil.currentBookmarkOpt.get());
            BookmarkUtil.currentBookmarkOpt = Optional.empty();
        } else {
            ItemStack itemStack = ScreenManager.getInstance().current()
                    .flatMap(BazaarScreenHandler::getDisplayItem)
                    .map(ItemInfo::itemStack)
                    .orElse(Items.DIAMOND.getDefaultStack());

            String productId = ScreenManager.getInstance().current()
                    .flatMap(BazaarScreenHandler::getDisplayProductId)
                    .orElse(null);

            MarketPrices bookmarkMarketPrices = new MarketPrices(productId);
            Bookmark newBookmark = new Bookmark(name, itemStack, bookmarkMarketPrices);
            list.add(newBookmark);

            BookmarkUtil.currentBookmarkOpt = Optional.of(newBookmark);
        }

        BookmarkUtil.saveBookmarks();
    }
}
