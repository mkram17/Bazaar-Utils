package com.github.mkram17.bazaarutils.utils.update;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import moe.nea.libautoupdate.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.concurrent.CompletableFuture;

public final class UpdateUtil {
    private static UpdateContext updateContext;

    private static UpdateContext getUpdateContext() {
        if (updateContext == null) {
            String versionTag = "v" + BazaarUtils.SELF.getMetadata().getVersion().getFriendlyString();

            updateContext = new UpdateContext(
                    new BazaarUtilsGithubSource(),
                    UpdateTarget.deleteAndSaveInTheSameFolder(BazaarUtils.class),
                    CurrentVersion.ofTag(versionTag),
                    BazaarUtils.MOD_ID
            );
        }

        return updateContext;
    }

    public static void updateModProperties() {
        ModMetadata metadata = BazaarUtils.SELF.getMetadata();

        CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
        if (updateNotesValue != null) {
            MetadataConfig.UPDATE_NOTES = updateNotesValue.getAsString();
        }

        String oldVersion = MetadataConfig.MOD_VERSION;
        String currentVersion = metadata.getVersion().getFriendlyString();
        MetadataConfig.MOD_VERSION = currentVersion;

        if (isMajorVersionChanged(oldVersion, currentVersion)) {
            MetadataConfig.UPDATED_MAJOR_VERSION = true;
        }

        ConfigUtil.scheduleConfigSave();
    }

    /**
     * Detects a major version bump using Fabric's VersionPredicate API.
     *
     * Strategy: parse both versions, extract the old major number, then build
     * a VersionPredicate ">= <oldMajor+1>.0.0" and test the new version against it.
     */
    private static boolean isMajorVersionChanged(String oldRaw, String newRaw) {
        if (oldRaw == null || oldRaw.isBlank()) return false;

        try {
            Version oldVersion = Version.parse(stripLeadingV(oldRaw));
            Version newVersion = Version.parse(stripLeadingV(newRaw));

            int oldMajor = extractMajor(oldVersion);

            // Build predicate: ">= <oldMajor+1>.0.0"
            // e.g. if old is 1.x.x, predicate is ">=2.0.0"
            // Uses VersionComparisonOperator.getSerialized() from fabric's stable api
            String predicateStr = VersionComparisonOperator.GREATER_EQUAL.getSerialized() + (oldMajor + 1) + ".0.0";
            VersionPredicate nextMajorBoundary = VersionPredicate.parse(predicateStr);

            return nextMajorBoundary.test(newVersion);
        } catch (Exception exception) {
            Util.logMessage("Could not compare versions '%s' and '%s': %s".formatted(oldRaw, newRaw, exception.getMessage()));

            return false;
        }
    }

    public static UpdateStream getUpdateStream() {
        return UpdateStream.fromVersion(MetadataConfig.MOD_VERSION);
    }

    public static void checkForUpdates() {
        getUpdateContext().cleanup();
        getUpdateContext().checkUpdate(getUpdateStream().toAutoUpdateKey()).thenCompose(update -> {
            if (!update.isUpdateAvailable()) {
                return CompletableFuture.completedFuture(null);
            }

            if (MetadataConfig.AUTO_UPDATE_ENABLED) {
                PlayerActionUtil.notifyAll("Successfully updated. Restart for changes to take effect.");
                return update.launchUpdate();
            } else {
                PlayerActionUtil.notifyAll("A new version of Bazaar Utils is available! To update, download it from the Modrinth/GitHub, or enable auto update.");
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    private static int extractMajor(Version version) {
        String[] parts = version.getFriendlyString().split("[.\\-+]", 2);
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String stripLeadingV(String version) {
        return (version.startsWith("v") || version.startsWith("V"))
                ? version.substring(1)
                : version;
    }
}
