package com.github.mkram17.bazaarutils.data.integrations;

import com.github.mkram17.bazaarutils.commands.activity.ExportActivityCommand;
import com.github.mkram17.bazaarutils.data.bazaar.activity.BazaarActivityRecord;
import com.github.mkram17.bazaarutils.data.stored.BazaarActivityStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capability domain for integrations that interact with Bazaar activity data.
 *
 * <p>An integration id may register multiple classes, each implementing one or more
 * sub-interfaces here. The registry enforces one instance per capability per id —
 * you cannot have two {@link StoragePrunable} implementations under the same id.
 */
sealed public interface BazaarActivityIntegration extends BazaarIntegrationCapability
        permits BazaarActivityIntegration.StoragePrunable,
        BazaarActivityIntegration.ActivityExportable {

    /**
     * Notified after {@link BazaarActivityStorage} drops records past the retention window.
     * Use this to evict any state (e.g. exported-UUID maps) keyed by the pruned record ids.
     * Called synchronously on the same thread as the prune.
     */
    non-sealed interface StoragePrunable extends BazaarActivityIntegration {
        void onActivityPruned(List<BazaarActivityRecord> pruned);
    }

    /**
     * Marks this integration as reachable via {@link ExportActivityCommand}.
     *
     * <p>{@code T} is the integration's own model type — the thing that gets serialized
     * and sent. It must implement {@link ExportEntry} so the command can collect
     * {@code sourceId → amount} pairs for {@link #mark} without knowing the concrete type.
     *
     * <p>{@link #pending()} returns only records not yet fully exported (or records
     * where new volume has accumulated since the last export). {@link #mark} is called
     * by the command after a successful delivery — do not call it yourself on failure.
     *
     * <p>{@link #reportSuccess} controls the player-facing message. Override it to add
     * clickable links, format-specific copy, or anything else. The default is a plain
     * green confirmation.
     *
     * <p>Implements {@link CapabilityManifest} — the display name and description shown
     * in command autocomplete.
     */
    non-sealed interface ActivityExportable<T extends ActivityExportable.ExportEntry> extends BazaarActivityIntegration, CapabilityManifest {
        List<T> pending();

        void mark(Map<UUID, Integer> amounts);

        String serialize(List<T> entries, ActivityExportable.ExportFormat format);

        List<ActivityExportable.ExportFormat> formats();

        ActivityExportable.ExportFormat defaultFormat();

        default Component reportSuccess(List<T> exported, String payload, ActivityExportable.ExportFormat format) {
            return Component.literal("Exported %d record(s).".formatted(exported.size())).withStyle(ChatFormatting.GREEN);
        }

        /** Minimum contract for anything the command needs to collect export amounts. */
        interface ExportEntry {
            UUID sourceId();

            int amount();
        }

        enum ExportFormat {
            JSON,
            BASE64,
            /** Integration-specific URL with base64-encoded payload as a query param payload. */
            LINK
        }
    }
}
