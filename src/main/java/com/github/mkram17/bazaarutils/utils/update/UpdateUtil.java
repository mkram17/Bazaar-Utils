package com.github.mkram17.bazaarutils.utils.update;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import moe.nea.libautoupdate.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.concurrent.CompletableFuture;

public final class UpdateUtil {
    private static UpdateContext updateContext;

    private static UpdateContext getUpdateContext() {
        if (updateContext == null) {
            String versionTag = "v" + BazaarUtils.MOD_CONTAINER.getMetadata().getVersion().getFriendlyString();

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
        ModMetadata metadata = BazaarUtils.MOD_CONTAINER.getMetadata();

        CustomValue updateNotesValue = metadata.getCustomValue("latestMinorUpdateNotes");
        if (updateNotesValue != null) {
            MetadataConfig.UPDATE_NOTES = updateNotesValue.getAsString();
        }

        String oldVersion = MetadataConfig.MOD_VERSION;
        String currentVersion = metadata.getVersion().getFriendlyString();
        MetadataConfig.MOD_VERSION = currentVersion;

        if (isMajorOrMinorUpgrade(oldVersion, currentVersion)) {
            MetadataConfig.SIGNIFICANT_VERSION_UPGRADE = true;
        }

        ConfigUtil.scheduleConfigSave();
    }

    /**
     * Checks if the new version is a major or minor upgrade compared to the old version.
     * This ignores patch updates and pre-release suffixes.
     */
    private static boolean isMajorOrMinorUpgrade(String oldRaw, String newRaw) {
        if (oldRaw == null || oldRaw.isBlank() || newRaw == null || newRaw.isBlank()) return false;

        try {
            Version oldVersion = Version.parse(stripLeadingV(oldRaw));
            Version newVersion = Version.parse(stripLeadingV(newRaw));

            if (!(oldVersion instanceof SemanticVersion oldSemantic && newVersion instanceof SemanticVersion newSemantic)) {
                return false;
            }

            int oldMajor = oldSemantic.getVersionComponent(0);
            int oldMinor = oldSemantic.getVersionComponent(1);

            int newMajor = newSemantic.getVersionComponent(0);
            int newMinor = newSemantic.getVersionComponent(1);

            return newMinor > oldMinor || newMajor > oldMajor;
        } catch (Exception exception) {
            Util.logMessage("Could not compare versions '%s' and '%s': %s".formatted(oldRaw, newRaw, exception.getMessage()));

            return false;
        }
    }

    public static UpdateStream getUpdateStream() {
        return UpdateStream.fromVersion(MetadataConfig.MOD_VERSION);
    }

    public static void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            UpdateContext context = getUpdateContext();
            context.cleanup();

            context.checkUpdate(getUpdateStream().toAutoUpdateKey())
                    .thenCompose(update -> {
                        if (!update.isUpdateAvailable()) {
                            Util.logMessage("Already up to date.");

                            return CompletableFuture.completedFuture(null);
                        }

                        if (BUConfig.AUTOMATIC_UPDATES_TOGGLE) {
                            return update.launchUpdate().thenRun(() -> PlayerActionUtil.notifyAll("Update downloaded! Restart to apply."));
                        } else {
                            PlayerActionUtil.notifyAll(
                                    "A new Bazaar Utils version is available! " +
                                            "Download it from Modrinth/GitHub, or enable auto-update in settings."
                            );

                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .exceptionally(exception -> {
                        Util.logMessage("Update check failed: %s".formatted(exception.getMessage()));

                        return null;
                    });

        }, BazaarUtils.BUExecutorService);
    }

    private static String stripLeadingV(String version) {
        return (version.startsWith("v") || version.startsWith("V"))
                ? version.substring(1)
                : version;
    }
}
