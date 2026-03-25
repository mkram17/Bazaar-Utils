package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class BookmarksStorage {
    private static final Type TYPE = new TypeToken<List<Bookmark>>(){}.getType();

    public static final DataStorage<List<Bookmark>> INSTANCE = new DataStorage<>(ArrayList::new, "bookmarks", TYPE);

    private BookmarksStorage() { }
}