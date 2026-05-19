package com.github.mkram17.bazaarutils.data.integrations.skyblockfinance.activity;

import com.github.mkram17.bazaarutils.data.integrations.BazaarActivityIntegration;
import com.github.mkram17.bazaarutils.data.integrations.skyblockfinance.SkyblockFinanceTradeModel;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import com.github.mkram17.bazaarutils.data.stored.integrations.SkyblockFinanceStorage;
import com.github.mkram17.bazaarutils.utils.annotations.modules.BazaarIntegration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Export capability for the skyblock.finance integration.
 *
 * <h2>Supported formats</h2>
 * Only {@link ExportFormat#LINK} is currently supported. The link encodes the payload
 * as base64 in a query parameter on {@code https://skyblock.finance/trades/import}.
 * {@code JSON} and {@code BASE64} can be added trivially — the codec and serialization
 * path are already in place.
 *
 * <h2>Pending export</h2>
 * {@link #pending()} runs {@link SkyblockFinanceTradeModel#HELD_TRADES} over the full
 * activity registry, then subtracts already-exported amounts from
 * {@link SkyblockFinanceStorage#INSTANCE}. Records where the subtracted amount reaches
 * zero are filtered out. The command calls {@link #mark} after successful delivery.
 */
@BazaarIntegration(id = "skyblock_finance")
public class SkyblockFinanceActivityExporter implements BazaarActivityIntegration.ActivityExportable<SkyblockFinanceTradeModel> {
    private static final String IMPORT_BASE_URL = "https://skyblock.finance/trades/import";

    @Override
    public String displayName() {
        return "skyblock.finance";
    }

    @Override
    public String description() {
        return "Exports held Bazaar positions to skyblock.finance's Trades feature.";
    }

    @Override
    public List<SkyblockFinanceTradeModel> pending() {
        var exported = SkyblockFinanceStorage.INSTANCE.get();
        if (exported == null) return List.of();

        return BazaarActivityStorage.fold(
                SkyblockFinanceTradeModel.HELD_TRADES.andThen(trades -> trades.stream()
                        .map(it -> it.subtractExported(exported.getOrDefault(it.sourceId(), 0)))
                        .filter(it -> it.amount() > 0)
                        .toList())
        );
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.LINK);
    }

    @Override
    public ExportFormat defaultFormat() {
        return ExportFormat.LINK;
    }

    /**
     * Marks units as exported. Call after a successful export with the amounts
     * returned from {@link #pending()}.
     */
    @Override
    public void mark(Map<UUID, Integer> amounts) {
        SkyblockFinanceStorage.INSTANCE.edit(map -> amounts.forEach((id, amount) -> map.merge(id, amount, Integer::sum)));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String serialize(List<SkyblockFinanceTradeModel> entries, ExportFormat format) {
        JsonElement encoded = Codec.list(SkyblockFinanceTradeModel.CODEC)
                .encodeStart(JsonOps.INSTANCE, entries)
                .getOrThrow();

        String json = GSON.toJson(encoded);

        return switch (format) {
            case LINK -> IMPORT_BASE_URL + "?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            default -> throw new UnsupportedOperationException("Not a target format.");
        };
    }

    @Override
    public Component reportSuccess(List<SkyblockFinanceTradeModel> exported, String payload, ExportFormat format) {
        var base = Component.literal("[skyblock.finance] Exported ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(exported.size() + " position(s)").withStyle(ChatFormatting.WHITE));

        return switch (format) {
            case LINK -> base.append(Component.literal(" — "))
                    .append(Component.literal("[Open import page]")
                            .withStyle(style -> {
                                try {
                                    return style
                                            .withColor(ChatFormatting.AQUA)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent.OpenUrl(new URI(payload)))
                                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(payload)));
                                } catch (URISyntaxException e) {
                                    throw new RuntimeException(e);
                                }
                            }));
            default -> throw new UnsupportedOperationException("Not a target format.");
        };
    }
}
