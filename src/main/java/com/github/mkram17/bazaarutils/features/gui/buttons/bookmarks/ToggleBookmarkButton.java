package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.data.stored.BookmarksStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.ItemGroups;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@ItemModifier
public class ToggleBookmarkButton extends BUListener implements ItemButton {
    @Override
    public int getSlotIndex() {
        return 0;
    }

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(() -> BookmarkUtil.currentPage().map(BookmarkUtil.PageContext::isBookmarked).orElse(false), ItemGroups.BOOKMARKED_STATE_GROUP);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    public ToggleBookmarkButton() {}

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        return BookmarkUtil.currentPage().isPresent() && ItemButton.super.appliesTo(stack, slot, context);
    }

    @Override
    public Optional<Component> nameOverride(ItemStack stack, @Nullable Slot slot) {
        return BookmarkUtil.currentPage().map(page -> Component.literal(
                page.isBookmarked()
                        ? "Remove " + page.name() + " Bookmark"
                        : "Bookmark " + page.name()));
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack, @Nullable Slot slot) {
        return BookmarkUtil.currentPage().map(page -> DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, page.isBookmarked() ? "⃠ " : "★")
                .build());
    }

    @Override
    public Result onButtonClicked(int button) {
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        BookmarkUtil.currentPage().ifPresent(this::toggleBookmark);

        return Result.CONSUMED;
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

        PlayerActionUtil.notifyAll("%s bookmark: %s".formatted(page.isBookmarked() ? "removed" : "added", page.name()), NotificationType.FEATURE);

        retriggerModifier();
    }
}
