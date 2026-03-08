package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

@Module
public class ToggleBookmarkButton extends BUListener implements ItemButton {
    @Getter
    private transient ItemStack replacementItem;

    private boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE);
    }

    public ToggleBookmarkButton() {}

    private Optional<String> resolveCurrentItemName() {
        return ScreenManager.getInstance()
                .current()
                .flatMap(BazaarScreenHandler::getDisplayItemName);
    }

    private void buildReplacementItem(String itemName) {
        boolean bookmarked = BookmarkUtil.currentBookmarkOpt.isPresent();

        this.replacementItem = new ItemStack(
                bookmarked ? Items.RED_STAINED_GLASS_PANE : Items.GREEN_STAINED_GLASS_PANE
        );

        replacementItem.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(bookmarked
                        ? "Remove " + BookmarkUtil.getCurrentBookmarkOpt().get().name() + " Bookmark"
                        : "Bookmark " + itemName)
        );

        replacementItem.set(
                BazaarUtils.CUSTOM_SIZE_COMPONENT,
                bookmarked ? "⃠ " : "★"
        );
    }

    @EventHandler
    private void onReplaceItemEvent(ReplaceItemEvent event) {
        if (!shouldReplaceItem(event) || !ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE)) {
            return;
        }

        resolveCurrentItemName().ifPresent(name -> {
            BookmarkUtil.currentBookmarkOpt = BookmarkUtil.findMatchingBookmark(name);
            buildReplacementItem(name);
            event.setReplacement(replacementItem);
        });
    }

    @EventHandler
    private void onClick(SlotClickEvent event) {
        if (!wasButtonSlotClicked(event) || !ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE)) {
            return;
        }

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

        buildReplacementItem(name);

        BookmarkUtil.saveBookmarks();
    }

    @Override
    public int getSlotNumber() {
        return 0;
    }
}
