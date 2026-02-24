package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

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

//TODO move config to config/bazaarutils directory and rename to "config". See how REI does this.
public class ResourceManager {

    private static final Path MOD_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID);
    private static final Path LOCAL_RESOURCES_PATH = MOD_CONFIG_DIR.resolve("bazaar-resources.json");
    private static final Identifier BUNDLED_RESOURCES_ID = Identifier.of(BazaarUtils.MOD_ID, "bazaar-resources.json");
    private static final String GITHUB_API_URL = "https://api.github.com/repos/mkram17/Skyblock-Bazaar-Conversions/contents/conversionupdating/bazaar-conversions.json?ref=main";


    public static void initialize() {
        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(MOD_CONFIG_DIR)) {
                    Files.createDirectories(MOD_CONFIG_DIR);
                }
                copyDefaultResourcesIfMissing();
                checkForUpdates(false); // Automatic check on startup
            } catch (IOException e) {
                Util.notifyError("Failed to initialize resource manager", e);
            }
        });
    }

    private static void copyDefaultResourcesIfMissing() throws IOException {
        if (Files.exists(LOCAL_RESOURCES_PATH)) {
            return;
        }

        Util.logMessage("Local resources file not found. Copying from bundled resources.");
        Optional<Resource> resourceOptional = MinecraftClient.getInstance().getResourceManager().getResource(BUNDLED_RESOURCES_ID);
        if (resourceOptional.isPresent()) {
            try (InputStream inputStream = resourceOptional.get().getInputStream()) {
                Files.copy(inputStream, LOCAL_RESOURCES_PATH);
                // don't know the SHA of the bundled file, so stays null to force an update check.
                MetadataConfig.RESOURCES_SHA = "";
                ConfigUtil.scheduleConfigSave();
            }
        } else {
            Util.notifyError("Could not find bundled bazaar-resources.json", null);
        }
    }

    public static void checkForUpdates(boolean manual) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URI(GITHUB_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() != 200) {
                    if (manual)
                        Util.notifyError("Failed to check for resource updates.", new Exception());
                    Util.logError("GitHub API responded with code: " + connection.getResponseCode(), null);
                    return;
                }

                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String latestSha = jsonObject.get("sha").getAsString();
                    String downloadUrl = jsonObject.get("download_url").getAsString();

                    if (!latestSha.equals(MetadataConfig.RESOURCES_SHA)) {
                        if (manual) {
                            PlayerActionUtil.notifyAll("New resources found, downloading...");
                        }
                        downloadLatestResources(downloadUrl, latestSha);
                    } else {
                        if (manual) {
                            PlayerActionUtil.notifyAll("Resources are already up-to-date.");
                        }
                    }
                }
            } catch (Exception e) {
                if (manual) {
                    Util.notifyError("An error occurred while checking for updates.", new Exception());
                }

                Util.notifyError("Failed to check for resource updates", e);
            }
        });
    }

    private static void downloadLatestResources(String downloadUrl, String latestSha) {
        Path tempPath = LOCAL_RESOURCES_PATH.resolveSibling("bazaar-resources.json.tmp");
        try (InputStream in = new URI(downloadUrl).toURL().openStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, LOCAL_RESOURCES_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            MetadataConfig.RESOURCES_SHA = latestSha;
            ConfigUtil.scheduleConfigSave();
            BazaarDataManager.setConversionsLoaded(false);
            PlayerActionUtil.notifyAll("Successfully updated Bazaar resources!");
        } catch (Exception e) {
            Util.notifyError("Failed to download resources", e);
            try {
                Files.deleteIfExists(tempPath); // Clean up the temporary file on failure
            } catch (IOException ex) {
                Util.logError("Failed to delete temporary resource file", ex);
            }
        }
    }

    public static JsonObject getResourceJson() {
        try {
            String content = Files.readString(LOCAL_RESOURCES_PATH);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (IOException e) {
            Util.notifyError("Could not read local bazaar-resources.json", e);
            // Fallback to bundled resources if local read fails
            try {
                Optional<Resource> resourceOptional = MinecraftClient.getInstance().getResourceManager().getResource(BUNDLED_RESOURCES_ID);
                if (resourceOptional.isPresent()) {
                    try (InputStream inputStream = resourceOptional.get().getInputStream()) {
                        String content = new String(inputStream.readAllBytes());
                        return JsonParser.parseString(content).getAsJsonObject();
                    }
                }
            } catch (IOException ex) {
                Util.notifyError("Fallback to bundled resources also failed", ex);
            }
        }
        return new JsonObject(); //empty (shouldnt happen)
    }

    @RunOnInit
    public static void onClientStart(){
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourceManager.initialize();
        });
    }
}