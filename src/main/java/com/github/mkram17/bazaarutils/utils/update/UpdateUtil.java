package com.github.mkram17.bazaarutils.utils.update;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import moe.nea.libautoupdate.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

public final class UpdateUtil {

    public static BazaarUtilsGithubSource githubSource = new BazaarUtilsGithubSource();
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

    public static void updateModProperties(){
        FabricLoader.getInstance().getModContainer(BazaarUtils.MOD_ID).ifPresent(modContainer -> {
            ModMetadata metadata = modContainer.getMetadata();

            CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
            if (updateNotesValue != null)
                MetadataConfig.UPDATE_NOTES = updateNotesValue.getAsString();

            var oldVersion = MetadataConfig.MOD_VERSION;
            var currentVersion = metadata.getVersion().getFriendlyString();

            MetadataConfig.MOD_VERSION = currentVersion;

            if (isMajorVersionChanged(oldVersion, currentVersion)) {
                MetadataConfig.UPDATED_MAJOR_VERSION = true;
            }

            ConfigUtil.scheduleConfigSave();
        });
    }

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

    private static String getCurrentVersionTag() {
        return FabricLoader.getInstance()
                .getModContainer(BazaarUtils.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(MetadataConfig.MOD_VERSION);
    }

    private static boolean isMajorVersionChanged(String oldVersion, String currentVersion) {
        Integer oldMajor = extractMajorVersion(oldVersion);
        Integer currentMajor = extractMajorVersion(currentVersion);

        if (oldMajor == null || currentMajor == null) {
            return false;
        }

        return !oldMajor.equals(currentMajor);
    }

    private static Integer extractMajorVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }

        String normalizedVersion = version.trim();
        if (normalizedVersion.startsWith("v") || normalizedVersion.startsWith("V")) {
            normalizedVersion = normalizedVersion.substring(1);
        }

        String coreVersion = normalizedVersion.split("[-+]", 2)[0];
        String firstSegment = coreVersion.split("\\.", 2)[0];
        Matcher matcher = LEADING_NUMBER.matcher(firstSegment);

        if (!matcher.find()) {
            return null;
        }

        return Integer.parseInt(matcher.group(1));
    }

    public static String getUpdateSource(){
        String currentVersion = MetadataConfig.MOD_VERSION;
        String currentStream = "full";

        if (currentVersion.toLowerCase().contains("alpha")) {
            currentStream = "alpha";
        } else if (currentVersion.toLowerCase().contains("beta")) {
            currentStream = "beta";
        }
        return currentStream;
    }

    public static void checkForUpdates() {
        getUpdateContext().cleanup();
        getUpdateContext().checkUpdate(getUpdateSource()).thenCompose(update -> {
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
}
