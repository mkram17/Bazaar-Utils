package com.github.mkram17.bazaarutils.data.stored;

import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

public final class BookmarksStorage {
    public static final DataStorage<List<Bookmark>> INSTANCE = new DataStorage<>(
            0,
            ArrayList::new,
            "bookmarks",
            v -> Codec.list(Bookmark.CODEC).xmap(ArrayList::new, ArrayList::new)
    );

    private BookmarksStorage() { }
}