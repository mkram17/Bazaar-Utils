package com.github.mkram17.bazaarutils.commands.deprecated;

import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;

@Command
@Deprecated
public final class DeprecatedSellRestrictionsCommand extends RetiredCommand {
    public DeprecatedSellRestrictionsCommand() {
        super("sellrestrictions", "the \"Instant Sell Rules\" category in the \"Inventory\" Mod Config");
    }
}
