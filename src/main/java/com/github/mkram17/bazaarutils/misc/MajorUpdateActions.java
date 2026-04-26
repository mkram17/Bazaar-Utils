package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks.BookmarkUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;

public class MajorUpdateActions {

    @RunOnInit
    public static void runIfUpdated(){
        if(!MetadataConfig.UPDATED_MAJOR_VERSION) return;
        // TODO: With the new storage api we can come with patches to serialized structures. Consider refactoring this.
        BookmarkUtil.getBookmarks().clear();
    }
}
