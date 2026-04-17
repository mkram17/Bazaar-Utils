package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.BookmarkUtil;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.storage.ProfileStorage;
import com.mojang.serialization.Codec;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BookmarksStorage {
    public static final ProfileStorage<List<Bookmark>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "bookmarks",
            v -> Codec.list(Bookmark.CODEC).xmap(ArrayList::new, ArrayList::new)
    );

    private BookmarksStorage() {}

    public static void add(Bookmark bookmark) {
        INSTANCE.edit(list -> list.add(bookmark));
    }

    public static void remove(Bookmark bookmark) {
        INSTANCE.edit(list -> list.remove(bookmark));
    }

    public static void clear() {
        INSTANCE.edit(List::clear);
    }
}