package com.github.mkram17.bazaarutils.commands;

import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.github.mkram17.bazaarutils.utils.web.AccountLinker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /bu link <code>} — connects this install to a Bazaar Utils website account.
 *
 * <p>With no argument it reports the current link instead, which is the only place in game that
 * surfaces it.</p>
 */
@Command
public final class LinkCommand implements BUCommand {
    @Getter
    public final String commandName = "link";

    @Getter
    public final Component description =
            Component.literal("Links your Minecraft account to the Bazaar Utils website.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base
                .then(ClientCommandManager.argument("code", StringArgumentType.word())
                        .executes(context -> {
                            AccountLinker.link(StringArgumentType.getString(context, "code"));

                            return 1;
                        }))
                .executes(context -> {
                    PlayerActionUtil.notifyAll(AccountLinker.status());

                    return 1;
                });
    }
}
