package com.github.mkram17.bazaarutils.commands.activity;

import com.github.mkram17.bazaarutils.commands.ActivityCommands;
import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.BazaarIntegrationRegistry;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Command(parent = ActivityCommands.class)
public final class ClearActivityCommand implements BUCommand {

    @Getter
    public final String commandName = "clear";

    @Getter
    public final Component description = Component.literal("Clears ALL stored activity records. Irreversible.").withStyle(ChatFormatting.RED);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.executes(this::clear);
    }

    private int clear(CommandContext<FabricClientCommandSource> ctx) {
        var storage = BazaarActivityStorage.INSTANCE.get();
        if (storage == null) {
            PlayerLogger.send("Storage not loaded.");

            return 0;
        }

        int count = storage.size();

        BazaarIntegrationRegistry.notify(
                BazaarActivityIntegration.StoragePrunable.class,
                integration -> integration.onActivityPruned(storage));

        storage.clear();
        BazaarActivityStorage.INSTANCE.save();

        PlayerLogger.send(Component.literal("Cleared %d activity record(s).".formatted(count)).withStyle(ChatFormatting.RED));

        return 1;
    }
}