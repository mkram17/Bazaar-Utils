package com.github.mkram17.bazaarutils.commands.deprecated;

import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * A command kept alive only to point at what replaced it. Registered so an old habit gets an
 * answer rather than "unknown command".
 */
@Deprecated
public abstract class RetiredCommand implements BUCommand {
    @Getter
    private final String commandName;

    private final String replacement;

    /**
     * @param replacement where the feature went, phrased to slot into the message below —
     *                    e.g. {@code the "Input Helpers" category in the "Buttons" Mod Config}
     */
    protected RetiredCommand(String commandName, String replacement) {
        this.commandName = commandName;
        this.replacement = replacement;
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(context -> {
            PlayerActionUtil.notifyAll("""
                This command has been deprecated as of version 1.0.0.

                To access the system replacing this feature, take a look at %s.
                """.formatted(replacement)
            );

            return 1;
        });
    }
}
