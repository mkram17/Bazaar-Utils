package com.github.mkram17.bazaarutils.commands.deprecated;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@Module
@Deprecated
public final class DeprecatedSellRestrictionsCommand implements BUCommand {
    @Getter
    public final String commandName = "sellrestrictions";

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            PlayerLogger.send("""
                This command has been deprecated as of version 1.0.0.
                
                To access the system replacing this feature, take a look at the "Instant Sell Rules" category in the "Inventory" Mod Config.
                """
            );

            return 1;
        });
    }
}