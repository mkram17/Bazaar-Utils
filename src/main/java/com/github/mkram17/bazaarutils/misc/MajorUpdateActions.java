package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.misc.autoregistration.RunOnInit;

public class MajorUpdateActions {

    @RunOnInit
    public static void runIfUpdated(){
        if(!BazaarUtils.updatedMajorVersion) return;
        BUConfig.get().bookmarks.clear();
    }
}
