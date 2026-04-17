package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.data.BookmarksStorage;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.ItemGroups;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.Optional;

@ItemModifier
public class ToggleBookmarkButton implements ItemButton {
    @Override
    public int getSlotIndex() {
        return 0;
    }

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(() -> BookmarkUtil.currentPage().map(BookmarkUtil.PageContext::isBookmarked).orElse(false), ItemGroups.BOOKMARKED_STATE_GROUP);
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return context.map(it -> it.equals(BazaarScreenType.ITEM_PAGE)).orElse(false);
    }

    public ToggleBookmarkButton() {}

    @Override
    public boolean appliesTo(ItemStack stack) {
        return BookmarkUtil.currentPage().isPresent() && ItemButton.super.appliesTo(stack);
    }

    @Override
    public Optional<Component> nameOverride(ItemStack stack) {
        return BookmarkUtil.currentPage().map(page -> Component.literal(
                page.isBookmarked()
                        ? "Remove " + page.name() + " Bookmark"
                        : "Bookmark " + page.name()));
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack) {
        return BookmarkUtil.currentPage().map(page -> DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, page.isBookmarked() ? "⃠ " : "★")
                .build());
    }

    @Override
    public Result onButtonClicked(int button) {
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        BookmarkUtil.currentPage().ifPresent(this::toggleBookmark);

        return Result.CONSUME;
    }

    private void toggleBookmark(BookmarkUtil.PageContext page) {
        if (page.isBookmarked()) {
            BookmarksStorage.remove(page.bookmark());
            BookmarkUtil.setCurrentBookmark(null);
        } else {
            Bookmark bookmark = page.toBookmark();
            BookmarksStorage.add(bookmark);
            BookmarkUtil.setCurrentBookmark(bookmark);
        }

        retriggerModifier();
    }
}