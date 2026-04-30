package com.github.mkram17.bazaarutils.utils.resources;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.DeveloperConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the lifecycle of the locally-cached bazaar-resources.json file.
 */
@PreInitModule
public final class BazaarConversionsUpdater extends BUListener {

    private static final Path MOD_CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID);

    private static final Path LOCAL_RESOURCES_PATH =
            MOD_CONFIG_DIR.resolve("bazaar-resources.json");
    private static final Identifier BUNDLED_RESOURCES_ID =
            Identifier.fromNamespaceAndPath(BazaarUtils.MOD_ID, "bazaar-resources.json");
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/mkram17/Skyblock-Bazaar-Conversions/contents/conversionupdating/bazaar-conversions.json?ref=main";

    public BazaarConversionsUpdater() {}

    @Override
    protected void registerFabricEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> initialize());
    }

    private static void initialize() {
        Util.logMessage("BazaarConversionsUpdater initialising — local path=%s".formatted(LOCAL_RESOURCES_PATH));

        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(MOD_CONFIG_DIR)) {
                    Files.createDirectories(MOD_CONFIG_DIR);
                }
                copyBundledIfMissing();
                if (!DeveloperConfig.DEVELOPER_MODE_DISABLE_AUTO_RESOURCE_UPDATES) checkForUpdates(false);
                BazaarConversions.ensureLoaded();
            } catch (IOException exception) {
                Util.notifyError("Failed to initialize resource manager", exception);
            }
        });
    }

    /**
     * Checks GitHub for a newer version of the resources file.
     *
     * @param manual {@code true} when triggered by the player (e.g. {@code /bu updateresources});
     *               shows chat feedback and errors. {@code false} for the silent startup check.
     */
    public static void checkForUpdates(boolean manual) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URI(GITHUB_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() != 200) {
                    Util.logError("GitHub API responded with code %s — skipping update check".formatted(connection.getResponseCode()), null);
                    if (manual) Util.notifyError("Failed to check for resource updates (HTTP " + connection.getResponseCode() + ")", new Exception());

                    return;
                }

                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String latestSha = json.get("sha").getAsString();
                    String downloadUrl = json.get("download_url").getAsString();

                    if (!latestSha.equals(MetadataConfig.RESOURCES_SHA)) {
                        Util.logMessage("Resource update available — downloading (sha=%s)".formatted(latestSha));
                        if (manual) PlayerActionUtil.notifyAll("New resources found, downloading...");

                        downloadAndReplace(downloadUrl, latestSha);
                    } else {
                        Util.logMessage("Resources up to date (sha=%s)".formatted(latestSha));
                        if (manual) PlayerActionUtil.notifyAll("Resources are already up-to-date.");
                    }
                }
            } catch (Exception exception) {
                Util.logError("Failed to check for resource updates", exception);
                if (manual) Util.notifyError("Failed to check for resource updates", new Exception());
            }
        });
    }

    private static void copyBundledIfMissing() throws IOException {
        if (Files.exists(LOCAL_RESOURCES_PATH)) return;
        Util.logMessage("Local resources file not found — copying from bundled resources");

        Optional<Resource> resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(BUNDLED_RESOURCES_ID);

        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                Files.copy(in, LOCAL_RESOURCES_PATH);
                // SHA unknown for bundled file — force update check on next run
                MetadataConfig.RESOURCES_SHA = "";
                ConfigUtil.scheduleConfigSave();
            }
        } else {
            Util.notifyError("Could not find bundled bazaar-resources.json — mod installation may be corrupted", null);
        }
    }

    private static void downloadAndReplace(String downloadUrl, String latestSha) {
        Path tempPath = LOCAL_RESOURCES_PATH.resolveSibling("bazaar-resources.json.tmp");

        try (InputStream in = new URI(downloadUrl).toURL().openStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, LOCAL_RESOURCES_PATH,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            MetadataConfig.RESOURCES_SHA = latestSha;
            ConfigUtil.scheduleConfigSave();

            BazaarConversions.invalidate();
            BazaarConversions.ensureLoaded();

            Util.logMessage("Resources updated successfully — sha=%s".formatted(latestSha));
            PlayerActionUtil.notifyAll("Successfully updated Bazaar resources!");
        } catch (Exception exception) {
            Util.notifyError("Failed to download resource update", exception);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                Util.logError("Failed to delete temporary resource file — path=%s".formatted(tempPath), ex);
            }
        }
    }

    /**
     * Reads the local resources file, falling back to the bundled copy on failure.
     *
     * @return parsed {@link JsonObject}, never null (empty object as last resort)
     */
    static JsonObject readResourceJson() {
        try {
            return JsonParser.parseString(Files.readString(LOCAL_RESOURCES_PATH)).getAsJsonObject();
        } catch (IOException exception) {
            Util.logError("Could not read local bazaar-resources.json — falling back to bundled", exception);
        }

        try {
            Optional<Resource> resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(BUNDLED_RESOURCES_ID);

            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return JsonParser.parseString(new String(in.readAllBytes())).getAsJsonObject();
                }
            }
        } catch (IOException ex) {
            Util.notifyError("Failed to read resource file and bundled fallback also failed", ex);
        }

        return new JsonObject();
    }
}