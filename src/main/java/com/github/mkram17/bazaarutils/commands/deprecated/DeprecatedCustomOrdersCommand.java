package com.github.mkram17.bazaarutils.commands.deprecated;

import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;

@Command
@Deprecated
public final class DeprecatedCustomOrdersCommand extends RetiredCommand {
    public DeprecatedCustomOrdersCommand() {
        super("customorders", "the \"Input Helpers\" category in the \"Buttons\" Mod Config");
    }
}
