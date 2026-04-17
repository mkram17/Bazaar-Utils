package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.BookmarksStorage;
import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.BookmarkUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;

public class MajorUpdateActions {

    @RunOnInit
    public static void runIfUpdated(){
        if(!BazaarUtils.updatedMajorVersion) return;
        // TODO: With the new storage api we can come with patches to serialized structures. Consider refactoring this.
        var storage = BookmarksStorage.INSTANCE.get();
        if (storage != null) storage.clear();
    }
}
