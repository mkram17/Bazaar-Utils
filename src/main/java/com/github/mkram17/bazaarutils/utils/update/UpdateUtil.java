package com.github.mkram17.bazaarutils.utils.update;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import moe.nea.libautoupdate.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.concurrent.CompletableFuture;

public final class UpdateUtil {

    public static BazaarUtilsGithubSource githubSource = new BazaarUtilsGithubSource();

    public static void updateModProperties(){
        FabricLoader.getInstance().getModContainer(BazaarUtils.MOD_ID).ifPresent(modContainer -> {
            ModMetadata metadata = modContainer.getMetadata();

            CustomValue updateNotesValue = metadata.getCustomValue("latestMajorUpdateNotes");
            if (updateNotesValue != null)
                MetadataConfig.UPDATE_NOTES = updateNotesValue.getAsString();

            var oldVersion = MetadataConfig.MOD_VERSION;
            var currentVersion = metadata.getVersion().getFriendlyString();

            var oldVersionMajor = oldVersion.substring(oldVersion.indexOf(".")+1);
            var currentVersionMajor = currentVersion.substring(currentVersion.indexOf(".")+1);

            MetadataConfig.MOD_VERSION = currentVersion;
            ConfigUtil.scheduleConfigSave();

            if (!oldVersionMajor.equals(currentVersionMajor)) {
                MetadataConfig.UPDATED_MAJOR_VERSION = true;
            }
        });
    }

    private static final UpdateContext updateContext = new UpdateContext(
            githubSource,
            UpdateTarget.deleteAndSaveInTheSameFolder(Main.class),
            CurrentVersion.ofTag("v" + MetadataConfig.MOD_VERSION),
            "bazaarutils"
    );

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
        updateContext.cleanup();
        updateContext.checkUpdate(getUpdateSource()).thenCompose(update -> {
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
