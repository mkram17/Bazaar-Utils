package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.storage.BookmarksStorage;
import lombok.Getter;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public class BookmarkUtil {
    @Getter
    public static Optional<Bookmark> currentBookmarkOpt = Optional.empty();

    public static final Identifier DEFAULT_WIDGET_TEXTURE = Identifier.tryBuild(BazaarUtils.MOD_ID, "widget/bookmark_widget_base");
    public static final Identifier HOVER_WIDGET_TEXTURE = Identifier.tryBuild(BazaarUtils.MOD_ID, "widget/bookmark_widget_hover");

    public static final WidgetSprites SLOT_BUTTON_TEXTURES = new WidgetSprites(DEFAULT_WIDGET_TEXTURE, HOVER_WIDGET_TEXTURE);

    public static void saveBookmarks() {
        BookmarksStorage.INSTANCE.save();
    }

    public static List<Bookmark> getBookmarks() {
        return BookmarksStorage.INSTANCE.get();
    }

    private BookmarkUtil() {}

    public static Optional<Bookmark> findMatchingBookmark(String itemName) {
        return BookmarkUtil.getBookmarks().stream()
                .filter(data -> data.name().equals(itemName))
                .findAny();
    }
}
