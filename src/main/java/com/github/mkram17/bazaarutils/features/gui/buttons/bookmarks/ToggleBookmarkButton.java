package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public class ToggleBookmarkButton extends BUListener implements ItemButton {
    @Getter
    private transient ItemStack replacementItem;

    private void buildReplacementItem() {
        boolean bookmarked = BookmarkUtil.currentBookmarkOpt.isPresent();

        this.replacementItem = new ItemStack(
                bookmarked ? Items.RED_STAINED_GLASS_PANE : Items.GREEN_STAINED_GLASS_PANE
        );

        replacementItem.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(bookmarked
                        ? "Remove " + BookmarkUtil.currentBookmarkOpt.get().getName() + " Bookmark"
                        : "Bookmark " + BookmarkUtil.findItemNameFromContainer())
        );

        replacementItem.set(
                BazaarUtils.CUSTOM_SIZE_COMPONENT,
                bookmarked ? "⃠ " : "★"
        );
    }

    @EventHandler
    private void onReplaceItemEvent(ReplaceItemEvent event) {
        if (!shouldReplaceItem(event) || !BookmarkUtil.inCorrectScreen()) {
            return;
        }

        String currentItemName = BookmarkUtil.findItemNameFromContainer();

        BookmarkUtil.currentBookmarkOpt = BookmarkUtil.findMatchingBookmark(currentItemName);
        buildReplacementItem();

        event.setReplacement(replacementItem);
    }

    @EventHandler
    private void onClick(SlotClickEvent event) {
        if (!wasButtonSlotClicked(event) || !BookmarkUtil.inCorrectScreen()) {
            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        toggleBookmark();
    }

    private void toggleBookmark() {
        String name = BookmarkUtil.findItemNameFromContainer();
        List<Bookmark> list = BookmarkUtil.getBookmarks();

        if (BookmarkUtil.currentBookmarkOpt.isPresent()) {
            list.remove(BookmarkUtil.currentBookmarkOpt.get());
            BookmarkUtil.currentBookmarkOpt = Optional.empty();
        } else {
            ItemStack actualItem = BookmarkUtil.findItemStack(name);

            Bookmark newBookmark = new Bookmark(name, actualItem, null);
            list.add(newBookmark);

            BookmarkUtil.currentBookmarkOpt = Optional.of(newBookmark);
        }

        buildReplacementItem();

        BookmarkUtil.saveBookmarks();
    }

    @Override
    public int getSlotNumber() {
        return 0;
    }
}
