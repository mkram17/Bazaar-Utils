package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.ItemGroups;
import net.minecraft.core.component.DataComponentPatch;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent;

import java.util.List;
import java.util.Optional;

@ItemModifier
public class ToggleBookmarkButton extends BUListener implements ItemButton {
    @Override
    public int getSlotIndex() {
        return 0;
    }

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(BookmarkUtil.currentBookmarkOpt::isEmpty, ItemGroups.BOOKMARKED_STATE_GROUP);
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return context.map(it -> it.isAnyOf(BazaarScreens.ITEM_PAGE)).orElse(false);
    }

    public ToggleBookmarkButton() {}

    @Override
    public Optional<Component> nameOverride(ItemStack stack) {
        boolean bookmarked = BookmarkUtil.currentBookmarkOpt.isPresent();

        return Optional.of(Component.literal(bookmarked
                ? "Remove " + BookmarkUtil.currentBookmarkOpt.get().name() + " Bookmark"
                : "Bookmark " + resolveCurrentItemName().orElse("?")));
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack) {
        boolean bookmarked = BookmarkUtil.currentBookmarkOpt.isPresent();

        return Optional.of(DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, bookmarked ? "⃠ " : "★")
                .build());
    }

    @Override
    public Result onButtonClicked(int button) {
        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        resolveCurrentItemName().ifPresent(this::toggleBookmark);

        return Result.CONSUME;
    }

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.ITEM_PAGE)
    private void onContainerInitialized(ContainerInitializedEvent event) {
        resolveCurrentItemName().ifPresent(name -> BookmarkUtil.currentBookmarkOpt = BookmarkUtil.findMatchingBookmark(name));
    }

    private void toggleBookmark(String name) {
        if (BookmarkUtil.currentBookmarkOpt.isPresent()) {
            BookmarkUtil.removeBookmark(BookmarkUtil.currentBookmarkOpt.get());
            BookmarkUtil.currentBookmarkOpt = Optional.empty();
        } else {
            ItemStack itemStack = ScreenManager.getInstance().current()
                    .flatMap(BazaarScreenHandler::getDisplayItem)
                    .map(ItemInfo::itemStack)
                    .orElse(Items.DIAMOND.getDefaultInstance());

            String productId = ScreenManager.getInstance().current()
                    .flatMap(BazaarScreenHandler::getDisplayProductId)
                    .orElse(null);

            Bookmark newBookmark = new Bookmark(name, itemStack, productId);
            BookmarkUtil.addBookmark(newBookmark);
            BookmarkUtil.currentBookmarkOpt = Optional.of(newBookmark);
        }

        retriggerModifier();
    }

    private static Optional<String> resolveCurrentItemName() {
        return ScreenManager.getInstance()
                .current()
                .flatMap(BazaarScreenHandler::getDisplayItemName);
    }
}