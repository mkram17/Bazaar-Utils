package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;

public final class BookmarksStorage {
    public static final ProfileStorage<List<Bookmark>> INSTANCE = new ProfileStorage<>(
            0,
            ArrayList::new,
            "bookmarks",
            v -> Codec.list(Bookmark.CODEC).xmap(ArrayList::new, list -> list)
    );

    private BookmarksStorage() {}
}