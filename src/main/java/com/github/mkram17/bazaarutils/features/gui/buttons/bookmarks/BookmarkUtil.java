package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.data.stored.BookmarksStorage;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.layouts.ProductPageLayout;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;

import java.util.Optional;

@Module
public final class BookmarkUtil extends BUListener {
    record PageContext(String productId, ItemStack itemStack, String name, @Nullable Bookmark bookmark) {
        boolean isBookmarked() {
            return bookmark != null;
        }

        Bookmark toBookmark() {
            return new Bookmark(name, itemStack, productId);
        }
    }

    private static @Nullable PageContext currentPage = null;

    public static void setCurrentBookmark(@Nullable Bookmark bookmark) {
        if (currentPage == null) return;
        currentPage = new PageContext(currentPage.productId(), currentPage.itemStack(), currentPage.name(), bookmark);
    }

    public static Optional<PageContext> currentPage() {
        return Optional.ofNullable(currentPage);
    }

    public BookmarkUtil() {}

    @Subscription
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(BazaarScreenType.PRODUCT_PAGE)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        var storage = BookmarksStorage.INSTANCE.get();
        if (storage == null) return;

        var context = event.asContext();

        var productId = ProductPageLayout.getDisplayProductInfo(context).orElse(null);
        var stack = ProductPageLayout.getDisplayItem(context).map(ItemInfo::itemStack).orElse(null);
        var name = Optional.ofNullable(stack).map(ItemStack::getCustomName).map(Component::getString).orElse(null);

        if (productId == null || name == null) {
            Util.logMessage("BookmarkUtil: no product info on ITEM_PAGE — clearing currentPage");
            currentPage = null;

            return;
        }

        Bookmark existing = storage.stream()
                .filter(bookmark -> bookmark.productId().equals(productId))
                .findFirst().orElse(null);

        PlayerActionUtil.notifyAll("%s — bookmarked=%b".formatted(productId, existing != null), NotificationType.FEATURE);

        currentPage = new PageContext(productId, stack, name, existing);
    }

    @Subscription
    private void onScreenChange(ScreenChangeEvent.Post event) {
        currentPage = null;
    }
}
