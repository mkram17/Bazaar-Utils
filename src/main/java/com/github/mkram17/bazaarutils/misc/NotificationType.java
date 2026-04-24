package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;

public enum NotificationType {
    COMPAT,          // REI / Skyblocker compatibility operations
    GUI,             // screen manager transitions and widget state: Screen nav, type resolution, history
    CHAT_PARSING,    // parser internals, slot-level and line-level
    SCREEN_PARSING,  // screen content → domain structure translation (PageOrderParser, PageSummaryParser)
    API,             // HTTP polling lifecycle
    PRICE_DATA,      // data source pipeline: price level changes, fill inference from book
    ORDER_LIFECYCLE, // processed event → book operations & final orders state
    ORDER_POSITION,  // position checks, transitions between positions
    STORAGE,         // file I/O load/save confirmations
    FEATURE,         // we're folding all features onto this for now, but we should consider each solution/feature to have their own enum
    COMMAND;         // developer command invocations and their output

    public boolean isEnabled() {
        return DeveloperConfig.isDeveloperVariableEnabled(this);
    }
}