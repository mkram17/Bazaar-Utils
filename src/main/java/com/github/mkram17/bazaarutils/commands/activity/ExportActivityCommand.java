package com.github.mkram17.bazaarutils.commands.activity;

import com.github.mkram17.bazaarutils.commands.ActivityCommands;
import com.github.mkram17.bazaarutils.commands.BUCommand;
import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.BazaarIntegrationRegistry;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Command(parent = ActivityCommands.class)
public final class ExportActivityCommand implements BUCommand {

    @Getter
    public final String commandName = "export";

    @Getter
    public final Component description = Component.literal("Exports activity to a registered integration target.").withStyle(ChatFormatting.GRAY);

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> getCommandBuilder(
            LiteralArgumentBuilder<FabricClientCommandSource> base) {
        return base.then(
                ClientCommandManager.argument("integration", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BazaarIntegrationRegistry.idsWith(BazaarActivityIntegration.ActivityExportable.class)
                                    .forEach(id -> builder.suggest(
                                            id,
                                            () -> BazaarIntegrationRegistry.get(id, BazaarActivityIntegration.ActivityExportable.class)
                                                    .map(BazaarActivityIntegration.ActivityExportable::displayName)
                                                    .orElse(id)
                                            )
                                    );

                            return builder.buildFuture();
                        })
                        .executes(ctx -> runExport(ctx, null))
                        .then(ClientCommandManager.argument("format", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String id = StringArgumentType.getString(ctx, "integration");

                                    BazaarIntegrationRegistry
                                            .get(id, BazaarActivityIntegration.ActivityExportable.class)
                                            .stream()
                                            .flatMap(exportable -> typed(exportable).formats().stream())
                                            .map(BazaarActivityIntegration.ActivityExportable.ExportFormat::name)
                                            .map(String::toLowerCase)
                                            .distinct()
                                            .forEach(builder::suggest);

                                    return builder.buildFuture();
                                })
                                .executes(ctx -> runExport(ctx, parseFormat(ctx))))
        );
    }

    private static int runExport(CommandContext<FabricClientCommandSource> ctx, BazaarActivityIntegration.ActivityExportable.ExportFormat explicitFormat) {
        String id = StringArgumentType.getString(ctx, "integration");

        var exportable = BazaarIntegrationRegistry.get(id, BazaarActivityIntegration.ActivityExportable.class);


        if (exportable.isEmpty()) {
            PlayerLogger.sendError("No exportable integration: " + id, null);

            return 0;
        }

        var displayName = exportable.get().displayName();

        var typed = typed(exportable.get());
        var format = explicitFormat != null ? explicitFormat : typed.defaultFormat();

        if (!typed.formats().contains(format)) {
            PlayerLogger.send(Component.literal("[" + displayName + "] Format " + format.name() + " not supported.").withStyle(ChatFormatting.RED));

            return 0;
        }

        var pending = typed.pending();
        if (pending.isEmpty()) {
            PlayerLogger.send(Component.literal("[" + displayName + "] Nothing new to export.").withStyle(ChatFormatting.YELLOW));

            return 1;
        }

        String payload = typed.serialize(pending, format);

        Map<UUID, Integer> exports = pending.stream().collect(
                Collectors.toMap(
                        BazaarActivityIntegration.ActivityExportable.ExportEntry::sourceId,
                        BazaarActivityIntegration.ActivityExportable.ExportEntry::amount,
                        Integer::sum));

        typed.mark(exports);

        PlayerLogger.send(typed.reportSuccess(pending, payload, format));

        return 1;
    }

    /**
     * Performs the unavoidable unchecked cast from raw {@code ActivityExportable}
     * to {@code ActivityExportable<T>}.
     */
    @SuppressWarnings("unchecked")
    private static <T extends BazaarActivityIntegration.ActivityExportable.ExportEntry>
    BazaarActivityIntegration.ActivityExportable<T> typed(BazaarActivityIntegration.ActivityExportable<?> raw) {
        return (BazaarActivityIntegration.ActivityExportable<T>) raw;
    }

    private static BazaarActivityIntegration.ActivityExportable.ExportFormat parseFormat(CommandContext<FabricClientCommandSource> ctx) {
        try {
            return BazaarActivityIntegration.ActivityExportable.ExportFormat.valueOf(StringArgumentType.getString(ctx, "format").toUpperCase());
        } catch (IllegalArgumentException e) {
            return BazaarActivityIntegration.ActivityExportable.ExportFormat.JSON;
        }
    }
}