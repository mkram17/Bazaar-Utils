package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.Bookmark;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class BookmarksStorage {
    private static final Type TYPE = new TypeToken<List<Bookmark>>(){}.getType();

    public static final DataStorage<List<Bookmark>> INSTANCE = new DataStorage<>(ArrayList::new, "bookmarks", TYPE);

    private BookmarksStorage() { }
}