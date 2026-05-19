package com.github.mkram17.bazaarutils.commands.activity;

import com.github.mkram17.bazaarutils.commands.ActivityCommands;
import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

@Command(parent = ActivityCommands.class)
public final class ListActivityCommand implements BUCommand {

    @Getter
    public final String commandName = "list";

    @Getter
    public final Component description = Component.literal("Lists all stored activity records.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base
                .executes(ctx -> listAll(ctx, null))
                .then(ClientCommandManager.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Stream.of("buy_order", "sell_offer", "flip", "instant_buy", "instant_sell").forEach(builder::suggest);

                            return builder.buildFuture();
                        })
                        .executes(ctx -> listAll(ctx, StringArgumentType.getString(ctx, "type"))));
    }

    private static int listAll(CommandContext<FabricClientCommandSource> ctx, @Nullable String typeFilter) {
        var all = BazaarActivityStorage.all();

        if (all.isEmpty()) {
            PlayerLogger.send("No activity records stored.");
            return 0;
        }

        var filtered = typeFilter == null ? all : all.stream()
                .filter(record -> record.type().equals(typeFilter))
                .toList();

        if (filtered.isEmpty()) {
            PlayerLogger.send("No records of type: " + typeFilter);

            return 0;
        }

        PlayerLogger.send(Component.literal("Activity records (%d total, %d shown):".formatted(all.size(), filtered.size())).withStyle(ChatFormatting.GREEN));

        for (int i = 0; i < filtered.size(); i++) {
            PlayerLogger.send(Component.literal("[%d] ".formatted(i))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(filtered.get(i).describe())));
        }

        return 1;
    }
}